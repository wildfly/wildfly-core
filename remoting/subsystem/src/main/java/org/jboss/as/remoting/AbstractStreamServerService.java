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

import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.network.ManagedBinding;
import org.jboss.as.network.NetworkUtils;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.as.remoting.logging.RemotingLogger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
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
import org.xnio.SslClientAuthMode;
import org.xnio.StreamConnection;
import org.xnio.channels.AcceptingChannel;

/**
 * Contains the remoting stream server
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @version $Revision: 1.1 $
 */
public abstract class AbstractStreamServerService implements Service<AcceptingChannel<StreamConnection>>{

    private final InjectedValue<SecurityRealm> securityRealm = new InjectedValue<>();
    private final InjectedValue<SaslAuthenticationFactory> saslAuthenticationFactory = new InjectedValue<>();
    private final InjectedValue<SSLContext> sslContext = new InjectedValue<>();
    private final InjectedValue<Endpoint> endpointValue = new InjectedValue<Endpoint>();
    private final InjectedValue<SocketBindingManager> socketBindingManagerValue = new InjectedValue<SocketBindingManager>();
    private final OptionMap connectorPropertiesOptionMap;

    private volatile AcceptingChannel<StreamConnection> streamServer;
    private volatile ManagedBinding managedBinding;

    AbstractStreamServerService(final OptionMap connectorPropertiesOptionMap) {
        this.connectorPropertiesOptionMap = connectorPropertiesOptionMap;
    }

    @Override
    public AcceptingChannel<StreamConnection> getValue() throws IllegalStateException, IllegalArgumentException {
        return streamServer;
    }

    public Injector<SecurityRealm> getSecurityRealmInjector() {
        return securityRealm;
    }

    public Injector<SaslAuthenticationFactory> getSaslAuthenticationFactoryInjector() {
        return saslAuthenticationFactory;
    }

    public Injector<SSLContext> getSSLContextInjector() {
        return sslContext;
    }

    public InjectedValue<Endpoint> getEndpointInjector() {
        return endpointValue;
    }

    public InjectedValue<SocketBindingManager> getSocketBindingManagerInjector() {
        return socketBindingManagerValue;
    }

    @Override
    public void start(final StartContext context) throws StartException {
        try {
            NetworkServerProvider networkServerProvider = endpointValue.getValue().getConnectionProviderInterface("remoting", NetworkServerProvider.class);

            SecurityRealm securityRealm = this.securityRealm.getOptionalValue();
            SSLContext sslContext = this.sslContext.getOptionalValue();
            if (sslContext == null && securityRealm != null) {
                sslContext = securityRealm.getSSLContext();
            }

            OptionMap.Builder builder = OptionMap.builder();
            if (sslContext != null) {
                builder.set(Options.SSL_ENABLED, true);
                builder.set(Options.SSL_STARTTLS, true);
            }

            final InjectedValue<SaslAuthenticationFactory> saslFactoryValue = this.saslAuthenticationFactory;
            SaslAuthenticationFactory factory = saslFactoryValue.getOptionalValue();
            if (factory == null && securityRealm != null) {
                factory = securityRealm.getSaslAuthenticationFactory();
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
            SocketBindingManager sbm = socketBindingManagerValue.getOptionalValue();
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
    public void stop(StopContext context) {
        IoUtils.safeClose(streamServer);
        SocketBindingManager sbm = socketBindingManagerValue.getOptionalValue();
        if (sbm != null && managedBinding != null) {
            unregisterSocketBinding(managedBinding, sbm);
        }
    }

    abstract InetSocketAddress getSocketAddress();

    abstract ManagedBinding registerSocketBinding(SocketBindingManager socketBindingManager);

    abstract void unregisterSocketBinding(ManagedBinding managedBinding, SocketBindingManager socketBindingManager);
}
