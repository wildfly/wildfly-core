/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.remoting;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.CallbackHandler;

import org.jboss.as.domain.management.CallbackHandlerFactory;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.network.NetworkUtils;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.as.remoting.logging.RemotingLogger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.value.InjectedValue;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.auth.client.MatchRule;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;

import static java.security.AccessController.doPrivileged;
import static org.xnio.Options.*;

/**
 * A {@link RemoteOutboundConnectionService} manages a remoting connection created out of a remote:// URI scheme.
 *
 * @author Jaikiran Pai
 */
public class RemoteOutboundConnectionService extends AbstractOutboundConnectionService<RemoteOutboundConnectionService> {

    public static final ServiceName REMOTE_OUTBOUND_CONNECTION_BASE_SERVICE_NAME = RemotingServices.SUBSYSTEM_ENDPOINT.append("remote-outbound-connection");

    private static final String JBOSS_LOCAL_USER = "JBOSS-LOCAL-USER";

    private static final AuthenticationContextConfigurationClient AUTH_CONFIGURATION_CLIENT = doPrivileged(AuthenticationContextConfigurationClient.ACTION);

    private final InjectedValue<OutboundSocketBinding> destinationOutboundSocketBindingInjectedValue = new InjectedValue<OutboundSocketBinding>();
    private final InjectedValue<SecurityRealm> securityRealmInjectedValue = new InjectedValue<SecurityRealm>();

    private final String username;
    private final String protocol;

    public RemoteOutboundConnectionService(final String connectionName, final OptionMap connectionCreationOptions, final String username, final String protocol) {
        super(connectionName, connectionCreationOptions);
        this.username = username;
        this.protocol = protocol;
    }

    @Override
    public IoFuture<Connection> connect() throws IOException {
        final URI uri;
        try {
            // we lazily generate the URI on first request to connect() instead of on start() of the service
            // in order to delay resolving the destination address. No point trying to resolve that address
            // if nothing really wants to create a connection out of it.
            uri = this.getConnectionURI();
        } catch (URISyntaxException e) {
            throw RemotingLogger.ROOT_LOGGER.couldNotConnect(e);
        }
        final Endpoint endpoint = this.endpointInjectedValue.getValue();

        AuthenticationContext captured = AuthenticationContext.captureCurrent();
        AuthenticationConfiguration mergedConfiguration = AUTH_CONFIGURATION_CLIENT.getAuthenticationConfiguration(uri, captured);

        final CallbackHandler callbackHandler;
        final CallbackHandlerFactory cbhFactory;
        SSLContext sslContext = null;
        SecurityRealm realm = securityRealmInjectedValue.getOptionalValue();
        if (realm != null && (cbhFactory = realm.getSecretCallbackHandlerFactory()) != null && username != null) {
            callbackHandler = cbhFactory.getCallbackHandler(username);
        } else {
            callbackHandler = null;
        }

        if (realm != null) {
            sslContext = realm.getSSLContext();
        }

        if (callbackHandler != null) mergedConfiguration = mergedConfiguration.useCallbackHandler(callbackHandler);
        if (sslContext != null) mergedConfiguration = mergedConfiguration.useSslContext(sslContext);

        final OptionMap.Builder builder = OptionMap.builder();
        // first set the defaults
        builder.set(SASL_POLICY_NOANONYMOUS, Boolean.FALSE);
        builder.set(SASL_POLICY_NOPLAINTEXT, Boolean.FALSE);
        builder.set(Options.SASL_DISALLOWED_MECHANISMS, Sequence.of(JBOSS_LOCAL_USER));
        Protocol protocol = Protocol.forName(uri.getScheme());
        switch (protocol) {
            case HTTP_REMOTING:
            case REMOTE_HTTP:
                builder.set(SSL_ENABLED, false);
                break;
            case HTTPS_REMOTING:
            case REMOTE_HTTPS:
                builder.set(SSL_ENABLED, true);
                builder.set(SSL_STARTTLS, false);
                break;
            default:
                builder.set(Options.SSL_ENABLED, true);
                builder.set(Options.SSL_STARTTLS, true);
                break;
        }

        // now override with user specified options
        builder.addAll(this.connectionCreationOptions);

        final AuthenticationContext context = AuthenticationContext.empty().with(MatchRule.ALL, mergedConfiguration);
        return endpoint.connect(uri, builder.getMap(), context);
    }

    @Override
    public String getProtocol() {
        return protocol;
    }

    Injector<OutboundSocketBinding> getDestinationOutboundSocketBindingInjector() {
        return this.destinationOutboundSocketBindingInjectedValue;
    }

    Injector<SecurityRealm> getSecurityRealmInjector() {
        return securityRealmInjectedValue;
    }

    /**
     * Generates and returns the URI that corresponds to the remote outbound connection.
     * If the URI has already been generated in a previous request, then it returns that back.
     * Else the URI is constructed out of the outbound socket binding's destination address and destination port.
     *
     * @return
     * @throws IOException
     * @throws URISyntaxException
     */
    private synchronized URI getConnectionURI() throws IOException, URISyntaxException {
        /* WFCORE-851 - do not cache connectionURI else reconnect will fail if DNS changes */
        final OutboundSocketBinding destinationOutboundSocket = this.destinationOutboundSocketBindingInjectedValue.getValue();
        final InetAddress destinationAddress = destinationOutboundSocket.getResolvedDestinationAddress();
        final int port = destinationOutboundSocket.getDestinationPort();

        return new URI(protocol + "://" + NetworkUtils.formatPossibleIpv6Address(destinationAddress.getHostAddress()) + ":" + port);
    }

    @Override
    public RemoteOutboundConnectionService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }
}
