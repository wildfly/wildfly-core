/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.protocol;

import static java.security.AccessController.doPrivileged;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.jboss.as.protocol.logging.ProtocolLogger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.auth.client.CallbackKind;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.auth.server.IdentityCredentials;
import org.wildfly.security.sasl.SaslMechanismSelector;
import org.xnio.IoFuture;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 * Protocol Connection utils.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ProtocolConnectionUtils {

    private static final String JBOSS_LOCAL_USER = "JBOSS-LOCAL-USER";

    private static final AuthenticationContextConfigurationClient AUTH_CONFIGURATION_CLIENT = doPrivileged(AuthenticationContextConfigurationClient.ACTION);

    /**
     * Connect.
     *
     * @param configuration the connection configuration
     * @return the future connection
     * @throws IOException
     */
    public static IoFuture<Connection> connect(final ProtocolConnectionConfiguration configuration) throws IOException {
        return connect(configuration.getCallbackHandler(), configuration);
    }

    public static IoFuture<Connection> connect(final ProtocolConnectionConfiguration configuration, CallbackHandler handler) throws IOException {
        final ProtocolConnectionConfiguration config = ProtocolConnectionConfiguration.copy(configuration);
        config.setCallbackHandler(handler);
        return connect(config);
    }

    public static IoFuture<Connection> connect(final ProtocolConnectionConfiguration configuration, CallbackHandler handler, Map<String, String> saslOptions, SSLContext sslContext) throws IOException {
        final ProtocolConnectionConfiguration config = ProtocolConnectionConfiguration.copy(configuration);
        config.setCallbackHandler(handler);
        config.setSaslOptions(saslOptions);
        config.setSslContext(sslContext);
        return connect(config);
    }

    /**
     * Connect sync.
     *
     * @param configuration the protocol configuration
     * @return the connection
     * @throws IOException
     */
    public static Connection connectSync(final ProtocolConnectionConfiguration configuration) throws IOException {
        long timeoutMillis = configuration.getConnectionTimeout();
        CallbackHandler handler = configuration.getCallbackHandler();
        final CallbackHandler actualHandler;
        ProtocolTimeoutHandler timeoutHandler = configuration.getTimeoutHandler();
        // Note: If a client supplies a ProtocolTimeoutHandler it is taking on full responsibility for timeout management.
        if (timeoutHandler == null) {
            GeneralTimeoutHandler defaultTimeoutHandler = new GeneralTimeoutHandler();
            // No point wrapping our AnonymousCallbackHandler.
            actualHandler = handler != null ? new WrapperCallbackHandler(defaultTimeoutHandler, handler) : null;
            timeoutHandler = defaultTimeoutHandler;
        } else {
            actualHandler = handler;
        }

        final IoFuture<Connection> future = connect(actualHandler, configuration);

        IoFuture.Status status = timeoutHandler.await(future, timeoutMillis);

        Connection result = checkFuture(status, future, configuration);
        if (result == null) {
            // Did not complete in time; tell remoting we don't want it
            future.cancel();
            // In case the future completed between when we waited for it and when we cancelled,
            // close any connection that was established. We don't want to risk using a
            // Connection after we told remoting to cancel, and if we don't use it we must close it.
            Connection toClose = checkFuture(future.getStatus(), future, configuration);
            StreamUtils.safeClose(toClose);

            throw ProtocolLogger.ROOT_LOGGER.couldNotConnect(configuration.getUri());
        }
        return result;
    }

    private static Connection checkFuture(IoFuture.Status status, IoFuture<Connection> future, ProtocolConnectionConfiguration configuration) throws IOException {
        if (status == IoFuture.Status.DONE) {
            return future.get();
        }
        if (status == IoFuture.Status.FAILED) {
            throw ProtocolLogger.ROOT_LOGGER.failedToConnect(configuration.getUri(), future.getException());
        }
        return null;
    }

    public static Connection connectSync(final ProtocolConnectionConfiguration configuration, CallbackHandler handler) throws IOException {
        final ProtocolConnectionConfiguration config = ProtocolConnectionConfiguration.copy(configuration);
        config.setCallbackHandler(handler);
        return connectSync(config);
    }

    public static Connection connectSync(final ProtocolConnectionConfiguration configuration, CallbackHandler handler, Map<String, String> saslOptions, SSLContext sslContext) throws IOException {
        final ProtocolConnectionConfiguration config = ProtocolConnectionConfiguration.copy(configuration);
        config.setCallbackHandler(handler);
        config.setSaslOptions(saslOptions);
        config.setSslContext(sslContext);
        return connectSync(config);
    }

    private static final EnumSet<CallbackKind> DEFAULT_CALLBACK_KINDS = EnumSet.of(
            CallbackKind.PRINCIPAL,
            CallbackKind.CREDENTIAL,
            CallbackKind.REALM
    );

    private static IoFuture<Connection> connect(final CallbackHandler handler, final ProtocolConnectionConfiguration configuration) throws IOException {
        configuration.validate();
        final Endpoint endpoint = configuration.getEndpoint();
        final URI uri = configuration.getUri();
        String clientBindAddress = configuration.getClientBindAddress();

        AuthenticationContext captured = AuthenticationContext.captureCurrent();
        AuthenticationConfiguration mergedConfiguration = AUTH_CONFIGURATION_CLIENT.getAuthenticationConfiguration(uri, captured);
        if (handler != null) {
            if (configuration.isCallbackHandlerPreferred()) {
                // Clear the three values as the CallbackHandler will be used for these.
                mergedConfiguration = mergedConfiguration.useAnonymous();
                mergedConfiguration = mergedConfiguration.useCredentials(IdentityCredentials.NONE);
                mergedConfiguration = mergedConfiguration.useRealm(null);
            }
            mergedConfiguration = mergedConfiguration.useCallbackHandler(handler, DEFAULT_CALLBACK_KINDS);
        }

        Map<String, String> saslOptions = configuration.getSaslOptions();
        mergedConfiguration = configureSaslMechanisms(saslOptions, isLocal(uri), mergedConfiguration);

        // Pass through any other SASL options from the ProtocolConnectionConfiguration
        // When we merge these, any pre-existing options already associated with the
        // AuthenticationConfiguration will take precedence.
        if (saslOptions != null) {
            saslOptions = new HashMap<>(saslOptions);
            // Drop SASL_DISALLOWED_MECHANISMS which we already handled
            saslOptions.remove(Options.SASL_DISALLOWED_MECHANISMS.getName());
            mergedConfiguration = mergedConfiguration.useSaslMechanismProperties(saslOptions);
        }

        SSLContext sslContext = configuration.getSslContext();
        if (sslContext == null) {
            try {
                sslContext = AUTH_CONFIGURATION_CLIENT.getSSLContext(uri, captured);
            } catch (GeneralSecurityException e) {
                throw ProtocolLogger.ROOT_LOGGER.failedToConnect(uri, e);
            }
        }

        // WFCORE-2342 check for default SSL / TLS options
        final OptionMap.Builder builder = OptionMap.builder();
        OptionMap optionMap = configuration.getOptionMap();
        for (Option option : optionMap) {
            builder.set(option, optionMap.get(option));
        }
        if (optionMap.get(Options.SSL_ENABLED) == null)
            builder.set(Options.SSL_ENABLED, configuration.isSslEnabled());
        if (optionMap.get(Options.SSL_STARTTLS) == null)
            builder.set(Options.SSL_STARTTLS, configuration.isUseStartTLS());

        AuthenticationContext authenticationContext = AuthenticationContext.empty();
        authenticationContext = authenticationContext.with(MatchRule.ALL, mergedConfiguration);
        final SSLContext finalSslContext = sslContext;
        authenticationContext = authenticationContext.withSsl(MatchRule.ALL, () -> finalSslContext);

        if (clientBindAddress == null) {
            return endpoint.connect(uri, builder.getMap(), authenticationContext);
        } else {
            InetSocketAddress bindAddr = new InetSocketAddress(clientBindAddress, 0);
            return endpoint.connect(uri, bindAddr, builder.getMap(), authenticationContext);
        }
    }

    private static AuthenticationConfiguration configureSaslMechanisms(Map<String, String> saslOptions, boolean isLocal, AuthenticationConfiguration authenticationConfiguration) {
        String[] mechanisms = null;
        String listed;
        if (saslOptions != null && (listed = saslOptions.get(Options.SASL_DISALLOWED_MECHANISMS.getName())) != null) {
            // Disallowed mechanisms were passed via the saslOptions map; need to convert to an XNIO option
            String[] split = listed.split(" ");
            if (isLocal) {
                mechanisms = new String[split.length + 1];
                mechanisms[0] = JBOSS_LOCAL_USER;
                System.arraycopy(split, 0, mechanisms, 1, split.length);
            } else {
                mechanisms = split;
            }
        } else if (!isLocal) {
            mechanisms = new String[]{ JBOSS_LOCAL_USER };
        }

        return (mechanisms != null && mechanisms.length > 0) ? authenticationConfiguration.setSaslMechanismSelector(SaslMechanismSelector.DEFAULT.forbidMechanisms(mechanisms)) : authenticationConfiguration;
    }

    private static boolean isLocal(final URI uri) {
        try {
            final String hostName = uri.getHost();
            final InetAddress address = InetAddress.getByName(hostName);
            NetworkInterface nic;
            if (address.isLinkLocalAddress()) {
                /*
                 * AS7-6382 On Windows the getByInetAddress was not identifying a NIC where the address did not have the zone
                 * ID, this manual iteration does allow for the address to be matched.
                 */
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                nic = null;
                while (interfaces.hasMoreElements() && nic == null) {
                    NetworkInterface current = interfaces.nextElement();
                    Enumeration<InetAddress> addresses = current.getInetAddresses();
                    while (addresses.hasMoreElements() && nic == null) {
                        InetAddress currentAddress = addresses.nextElement();
                        if (address.equals(currentAddress)) {
                            nic = current;
                        }
                    }
                }
            } else {
                nic = NetworkInterface.getByInetAddress(address);
            }
            return address.isLoopbackAddress() || nic != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static final class WrapperCallbackHandler implements CallbackHandler {

        private final GeneralTimeoutHandler timeoutHandler;
        private final CallbackHandler wrapped;

        WrapperCallbackHandler(final GeneralTimeoutHandler timeoutHandler, final CallbackHandler toWrap) {
            this.timeoutHandler = timeoutHandler;
            this.wrapped = toWrap;
        }

        public void handle(final Callback[] callbacks) throws IOException, UnsupportedCallbackException {

            try {
                timeoutHandler.suspendAndExecute(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            wrapped.handle(callbacks);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        } catch (UnsupportedCallbackException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            } catch (RuntimeException e) {
                if (e.getCause() instanceof IOException) {
                    throw (IOException) e.getCause();
                } else if (e.getCause() instanceof UnsupportedCallbackException) {
                    throw (UnsupportedCallbackException) e.getCause();
                }
                throw e;
            }
        }

    }

}
