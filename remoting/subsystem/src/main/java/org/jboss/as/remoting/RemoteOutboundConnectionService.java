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

import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;

import org.jboss.as.domain.management.CallbackHandlerFactory;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.network.NetworkUtils;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.as.remoting.logging.RemotingLogger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.remoting3.RemotingOptions;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.sasl.SaslMechanismSelector;
import org.xnio.OptionMap;

import static java.security.AccessController.doPrivileged;

import javax.net.ssl.SSLContext;

/**
 * A {@link RemoteOutboundConnectionService} manages a remoting connection created out of a remote:// URI scheme.
 *
 * @author Jaikiran Pai
 */
public class RemoteOutboundConnectionService extends AbstractOutboundConnectionService implements Service<RemoteOutboundConnectionService> {

    public static final ServiceName REMOTE_OUTBOUND_CONNECTION_BASE_SERVICE_NAME = RemotingServices.SUBSYSTEM_ENDPOINT.append("remote-outbound-connection");

    private static final String JBOSS_LOCAL_USER = "JBOSS-LOCAL-USER";

    private static final AuthenticationContextConfigurationClient AUTH_CONFIGURATION_CLIENT = doPrivileged(AuthenticationContextConfigurationClient.ACTION);

    private final InjectedValue<OutboundSocketBinding> destinationOutboundSocketBindingInjectedValue = new InjectedValue<OutboundSocketBinding>();
    private final InjectedValue<SecurityRealm> securityRealmInjectedValue = new InjectedValue<SecurityRealm>();
    private final InjectedValue<AuthenticationContext> authenticationContext = new InjectedValue<>();

    private final OptionMap connectionCreationOptions;
    private final String username;
    private final String protocol;

    private AuthenticationConfiguration configuration;
    private URI destination;
    private SSLContext sslContext;

    public RemoteOutboundConnectionService(final OptionMap connectionCreationOptions, final String username, final String protocol) {
        super();
        this.connectionCreationOptions = connectionCreationOptions;
        this.username = username;
        this.protocol = protocol;
    }

    Injector<OutboundSocketBinding> getDestinationOutboundSocketBindingInjector() {
        return this.destinationOutboundSocketBindingInjectedValue;
    }

    Injector<SecurityRealm> getSecurityRealmInjector() {
        return securityRealmInjectedValue;
    }

    Injector<AuthenticationContext> getAuthenticationContextInjector() {
        return authenticationContext;
    }

    public void start(final StartContext context) throws StartException {
        AuthenticationConfiguration configuration;
        final OutboundSocketBinding binding = destinationOutboundSocketBindingInjectedValue.getValue();
        final String hostName = NetworkUtils.formatPossibleIpv6Address(binding.getUnresolvedDestinationAddress());
        final int port = binding.getDestinationPort();
        URI uri;
        final String username = this.username;
        final SSLContext sslContext;
        try {
            uri = new URI(protocol, username, hostName, port, null, null, null);
        } catch (URISyntaxException e) {
            throw new StartException(e);
        }
        final AuthenticationContext injectedContext = this.authenticationContext.getOptionalValue();
        if (injectedContext != null) {
            configuration = AUTH_CONFIGURATION_CLIENT.getAuthenticationConfiguration(uri, injectedContext, -1, null, null);
            try {
                sslContext = AUTH_CONFIGURATION_CLIENT.getSSLContext(uri, injectedContext);
            } catch (GeneralSecurityException e) {
                throw RemotingLogger.ROOT_LOGGER.failedToObtainSSLContext(e);
            }
            // if the protocol is specified in the authentication configuration, use it in the destination URI
            final String realProtocol = AUTH_CONFIGURATION_CLIENT.getRealProtocol(configuration);
            try {
                uri = new URI(realProtocol == null ? Protocol.HTTP_REMOTING.toString() : realProtocol, username, hostName, port, null, null, null);
            } catch (URISyntaxException e) {
                throw new StartException(e);
            }
        } else {
            final SecurityRealm securityRealm = securityRealmInjectedValue.getOptionalValue();
            if (securityRealm != null) {
                // legacy remote-outbound-connection configuration
                configuration = AuthenticationConfiguration.empty();
                if (username != null) {
                    configuration = configuration
                            .useName(username)
                            .setSaslMechanismSelector(SaslMechanismSelector.DEFAULT.forbidMechanism(JBOSS_LOCAL_USER));
                    final CallbackHandlerFactory callbackHandlerFactory = securityRealm.getSecretCallbackHandlerFactory();
                    if (callbackHandlerFactory != null) {
                        configuration = configuration.useCallbackHandler(callbackHandlerFactory.getCallbackHandler(username));
                    }
                }
                sslContext = securityRealm.getSSLContext();
            } else {
                configuration = AuthenticationConfiguration.empty();
                sslContext = null;
            }
        }
        final OptionMap optionMap = this.connectionCreationOptions;
        if (optionMap != null) {
            configuration = RemotingOptions.mergeOptionsIntoAuthenticationConfiguration(optionMap, configuration);
        }
        this.configuration = configuration;
        this.destination = uri;
        this.sslContext = sslContext;
    }

    public void stop(final StopContext context) {
        this.configuration = null;
    }

    public AuthenticationConfiguration getAuthenticationConfiguration() {
        return configuration;
    }

    public SSLContext getSSLContext() {
        return sslContext;
    }

    public URI getDestinationUri() {
        return destination;
    }

    @Override
    public RemoteOutboundConnectionService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }
}
