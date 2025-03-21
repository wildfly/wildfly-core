/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import java.net.BindException;
import java.net.InetSocketAddress;

import javax.net.ssl.SSLContext;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.network.ManagedBinding;
import org.jboss.as.network.NetworkUtils;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.as.remoting.logging.RemotingLogger;
import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.wildfly.security.auth.permission.LoginPermission;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.sasl.anonymous.AnonymousServerFactory;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.channels.AcceptingChannel;

/**
 * Contains the remoting stream server
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
abstract class AbstractStreamServerService implements Service {

    private final Consumer<AcceptingChannel<StreamConnection>> streamServerConsumer;
    /* not private due to testing purposes */ final Supplier<Endpoint> endpointSupplier;
    private final Supplier<SaslAuthenticationFactory> saslAuthenticationFactorySupplier;
    private final Supplier<SSLContext> sslContextSupplier;
    private final Supplier<SocketBindingManager> socketBindingManagerSupplier;
    private final OptionMap connectorPropertiesOptionMap;

    private final String protocol;

    private volatile AcceptingChannel<StreamConnection> streamServer;
    private volatile ManagedBinding managedBinding;

    AbstractStreamServerService(
            final Consumer<AcceptingChannel<StreamConnection>> streamServerConsumer,
            final Supplier<Endpoint> endpointSupplier,
            final Supplier<SaslAuthenticationFactory> saslAuthenticationFactorySupplier,
            final Supplier<SSLContext> sslContextSupplier,
            final Supplier<SocketBindingManager> socketBindingManagerSupplier,
            final OptionMap connectorPropertiesOptionMap,
            final String protocol) {
        this.streamServerConsumer = streamServerConsumer;
        this.endpointSupplier = endpointSupplier;
        this.saslAuthenticationFactorySupplier = saslAuthenticationFactorySupplier;
        this.sslContextSupplier = sslContextSupplier;
        this.socketBindingManagerSupplier = socketBindingManagerSupplier;
        this.connectorPropertiesOptionMap = connectorPropertiesOptionMap;
        this.protocol = protocol;
    }

    @Override
    public void start(final StartContext context) throws StartException {
        try {
            NetworkServerProvider networkServerProvider = endpointSupplier.get().getConnectionProviderInterface(protocol, NetworkServerProvider.class);

            SSLContext sslContext = sslContextSupplier != null ? sslContextSupplier.get() : null;

            OptionMap.Builder builder = OptionMap.builder();
            if (sslContext != null) {
                builder.set(Options.SSL_ENABLED, true);
                builder.set(Options.SSL_STARTTLS, true);
            }

            SaslAuthenticationFactory factory = saslAuthenticationFactorySupplier != null ? saslAuthenticationFactorySupplier.get() : null;
            if (connectorPropertiesOptionMap != null) {
                builder.addAll(connectorPropertiesOptionMap);
            }
            OptionMap resultingMap = builder.getMap();
            if (RemotingLogger.ROOT_LOGGER.isTraceEnabled()) {
                RemotingLogger.ROOT_LOGGER.tracef("Resulting OptionMap %s", resultingMap.toString());
            }

            if (factory == null) {
                // TODO Elytron: Just authenticate anonymously
                RemotingLogger.ROOT_LOGGER.warn("****** All authentication is ANONYMOUS for " + getClass().getName());
                final SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
                domainBuilder.setPermissionMapper((permissionMappable, roles) -> LoginPermission.getInstance());
                domainBuilder.addRealm("default", org.wildfly.security.auth.server.SecurityRealm.EMPTY_REALM).build();
                domainBuilder.setDefaultRealmName("default");
                factory = SaslAuthenticationFactory
                    .builder()
                    .setFactory(new AnonymousServerFactory())
                    .setMechanismConfigurationSelector(i -> MechanismConfiguration.EMPTY)
                    .setSecurityDomain(domainBuilder.build())
                    .build();
            }
            streamServer = networkServerProvider.createServer(getSocketAddress(), resultingMap, factory, sslContext);
            streamServerConsumer.accept(streamServer);
            SocketBindingManager sbm = socketBindingManagerSupplier != null ? socketBindingManagerSupplier.get() : null;
            if (sbm != null) {
                managedBinding = registerSocketBinding(sbm);
            }
            RemotingLogger.ROOT_LOGGER.listeningOnSocket(NetworkUtils.formatAddress(getSocketAddress()));

        } catch (BindException e) {
            throw RemotingLogger.ROOT_LOGGER.couldNotBindToSocket(e.getMessage() + " " + NetworkUtils.formatAddress(getSocketAddress()), e);
        } catch (Exception e) {
            throw RemotingLogger.ROOT_LOGGER.couldNotStart(e);
        }
    }

    @Override
    public void stop(final StopContext context) {
        streamServerConsumer.accept(null);
        IoUtils.safeClose(streamServer);
        SocketBindingManager sbm = socketBindingManagerSupplier != null ? socketBindingManagerSupplier.get() : null;
        if (sbm != null && managedBinding != null) {
            unregisterSocketBinding(managedBinding, sbm);
        }
    }

    abstract InetSocketAddress getSocketAddress();

    abstract ManagedBinding registerSocketBinding(SocketBindingManager socketBindingManager);

    abstract void unregisterSocketBinding(ManagedBinding managedBinding, SocketBindingManager socketBindingManager);

}
