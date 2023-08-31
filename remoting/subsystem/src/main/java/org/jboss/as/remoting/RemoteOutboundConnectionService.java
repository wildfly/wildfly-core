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
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.network.NetworkUtils;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.as.remoting.logging.RemotingLogger;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.remoting3.RemotingOptions;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.xnio.OptionMap;

import static java.security.AccessController.doPrivileged;

import javax.net.ssl.SSLContext;

/**
 * A {@link RemoteOutboundConnectionService} manages a remoting connection created out of a remote:// URI scheme.
 *
 * @author Jaikiran Pai
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class RemoteOutboundConnectionService extends AbstractOutboundConnectionService implements Service {

    static final ServiceName REMOTE_OUTBOUND_CONNECTION_BASE_SERVICE_NAME = RemotingServices.SUBSYSTEM_ENDPOINT.append("remote-outbound-connection");
    private static final String JBOSS_LOCAL_USER = "JBOSS-LOCAL-USER";
    private static final AuthenticationContextConfigurationClient AUTH_CONFIGURATION_CLIENT = doPrivileged(AuthenticationContextConfigurationClient.ACTION);

    private final Consumer<RemoteOutboundConnectionService> serviceConsumer;
    private final Supplier<OutboundSocketBinding> outboundSocketBindingSupplier;
    private final Supplier<AuthenticationContext> authenticationContextSupplier;

    private final OptionMap connectionCreationOptions;
    private final String username;
    private final String protocol;

    private volatile URI destination;
    private volatile SSLContext sslContext;
    private volatile Supplier<AuthenticationConfiguration> authenticationConfiguration;

    RemoteOutboundConnectionService(
            final Consumer<RemoteOutboundConnectionService> serviceConsumer,
            final Supplier<OutboundSocketBinding> outboundSocketBindingSupplier,
            final Supplier<AuthenticationContext> authenticationContextSupplier,
            final OptionMap connectionCreationOptions, final String username, final String protocol) {
        this.serviceConsumer = serviceConsumer;
        this.outboundSocketBindingSupplier = outboundSocketBindingSupplier;
        this.authenticationContextSupplier = authenticationContextSupplier;
        this.connectionCreationOptions = connectionCreationOptions;
        this.username = username;
        this.protocol = protocol;
    }

    @Override
    public void start(final StartContext context) throws StartException {
        final OutboundSocketBinding binding = outboundSocketBindingSupplier.get();
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
        final AuthenticationContext injectedContext = authenticationContextSupplier != null ? authenticationContextSupplier.get() : null;
        if (injectedContext != null) {
            AuthenticationConfiguration configuration = AUTH_CONFIGURATION_CLIENT.getAuthenticationConfiguration(uri, injectedContext, -1, null, null);
            try {
                sslContext = AUTH_CONFIGURATION_CLIENT.getSSLContext(uri, injectedContext);
            } catch (GeneralSecurityException e) {
                throw RemotingLogger.ROOT_LOGGER.failedToObtainSSLContext(e);
            }
            // if the protocol is specified in the authentication configuration, use it in the destination URI
            final String realProtocol = AUTH_CONFIGURATION_CLIENT.getRealProtocol(configuration);
            try {
                uri = new URI(realProtocol == null ? Protocol.REMOTE_HTTP.toString() : realProtocol, username, hostName, port, null, null, null);
            } catch (URISyntaxException e) {
                throw new StartException(e);
            }
            URI finalUri = uri;
            authenticationConfiguration = () -> AUTH_CONFIGURATION_CLIENT.getAuthenticationConfiguration(finalUri, injectedContext);
        } else {
            AuthenticationConfiguration configuration = AuthenticationConfiguration.empty();
            sslContext = null;

            AuthenticationConfiguration finalConfiguration = configuration;
            authenticationConfiguration = () -> finalConfiguration;
        }
        this.destination = uri;
        this.sslContext = sslContext;
        this.serviceConsumer.accept(this);
    }

    @Override
    public void stop(final StopContext context) {
        serviceConsumer.accept(null);
        authenticationConfiguration = null;
        destination = null;
        sslContext = null;
    }

    @Override
    public AuthenticationConfiguration getAuthenticationConfiguration() {
        final AuthenticationConfiguration authenticationConfiguration = this.authenticationConfiguration.get();
        final OptionMap optionMap = this.connectionCreationOptions;
        if (optionMap != null) {
            return RemotingOptions.mergeOptionsIntoAuthenticationConfiguration(optionMap, authenticationConfiguration);
        }
        return authenticationConfiguration;
    }

    @Override
    public SSLContext getSSLContext() {
        return sslContext;
    }

    @Override
    public URI getDestinationUri() {
        return destination;
    }
}
