/*
* JBoss, Home of Professional Open Source.
* Copyright 2006, Red Hat Middleware LLC, and individual contributors
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

import java.net.BindException;
import java.net.InetSocketAddress;

import javax.net.ssl.SSLContext;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.SecurityRealm;
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
import org.xnio.Sequence;
import org.xnio.SslClientAuthMode;
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
    private final Supplier<SecurityRealm> securityRealmSupplier;
    private final Supplier<SaslAuthenticationFactory> saslAuthenticationFactorySupplier;
    private final Supplier<SSLContext> sslContextSupplier;
    private final Supplier<SocketBindingManager> socketBindingManagerSupplier;
    private final OptionMap connectorPropertiesOptionMap;

    private volatile AcceptingChannel<StreamConnection> streamServer;
    private volatile ManagedBinding managedBinding;

    AbstractStreamServerService(
            final Consumer<AcceptingChannel<StreamConnection>> streamServerConsumer,
            final Supplier<Endpoint> endpointSupplier,
            final Supplier<SecurityRealm> securityRealmSupplier,
            final Supplier<SaslAuthenticationFactory> saslAuthenticationFactorySupplier,
            final Supplier<SSLContext> sslContextSupplier,
            final Supplier<SocketBindingManager> socketBindingManagerSupplier,
            final OptionMap connectorPropertiesOptionMap) {
        this.streamServerConsumer = streamServerConsumer;
        this.endpointSupplier = endpointSupplier;
        this.securityRealmSupplier = securityRealmSupplier;
        this.saslAuthenticationFactorySupplier = saslAuthenticationFactorySupplier;
        this.sslContextSupplier = sslContextSupplier;
        this.socketBindingManagerSupplier = socketBindingManagerSupplier;
        this.connectorPropertiesOptionMap = connectorPropertiesOptionMap;
    }

    @Override
    public void start(final StartContext context) throws StartException {
        try {
            NetworkServerProvider networkServerProvider = endpointSupplier.get().getConnectionProviderInterface("remoting", NetworkServerProvider.class);

            SecurityRealm securityRealm = securityRealmSupplier != null ? securityRealmSupplier.get() : null;
            SSLContext sslContext = sslContextSupplier != null ? sslContextSupplier.get() : null;
            if (sslContext == null && securityRealm != null) {
                sslContext = securityRealm.getSSLContext();
            }

            OptionMap.Builder builder = OptionMap.builder();
            if (sslContext != null) {
                builder.set(Options.SSL_ENABLED, true);
                builder.set(Options.SSL_STARTTLS, true);
            }

            SaslAuthenticationFactory factory = saslAuthenticationFactorySupplier != null ? saslAuthenticationFactorySupplier.get() : null;
            if (factory == null && securityRealm != null) {
                String[] mechanismNames = null;
                if(connectorPropertiesOptionMap.contains(Options.SASL_MECHANISMS)) {
                    Sequence<String> sequence = connectorPropertiesOptionMap.get(Options.SASL_MECHANISMS);
                    mechanismNames = sequence.toArray(new String[sequence.size()]);
                }

                //in case that legacy sasl mechanisms are used, noanonymous default value is true
                Boolean policyNonanonymous = mechanismNames == null ? null: true;
                if(connectorPropertiesOptionMap.contains(Options.SASL_POLICY_NOANONYMOUS)) {
                    policyNonanonymous = connectorPropertiesOptionMap.get(Options.SASL_POLICY_NOANONYMOUS).booleanValue();
                }

                if(mechanismNames != null || policyNonanonymous != null) {
                    factory = securityRealm.getSaslAuthenticationFactory(mechanismNames, policyNonanonymous);
                } else {
                    factory = securityRealm.getSaslAuthenticationFactory();
                }

                if (securityRealm.getSupportedAuthenticationMechanisms().contains(AuthMechanism.CLIENT_CERT)) {
                    builder.set(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.REQUESTED);
                }
            }

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
