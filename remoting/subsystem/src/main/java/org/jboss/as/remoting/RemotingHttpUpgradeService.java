/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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


import static org.jboss.as.remoting.Capabilities.SASL_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.jboss.as.remoting.Protocol.REMOTE_HTTP;
import static org.jboss.as.remoting.Protocol.REMOTE_HTTPS;

import java.io.IOException;
import java.util.function.Consumer;

import io.undertow.server.ListenerRegistry;
import io.undertow.server.handlers.ChannelUpgradeHandler;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.remoting.logging.RemotingLogger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.UnknownURISchemeException;
import org.jboss.remoting3.spi.ExternalConnectionProvider;
import org.wildfly.security.auth.permission.LoginPermission;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.wildfly.security.sasl.anonymous.AnonymousServerFactory;
import org.xnio.ChannelListener;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.StreamConnection;

/**
 * Service that registers a HTTP upgrade handler to enable remoting to be used via http upgrade.
 *
 * @author Stuart Douglas
 */
public class RemotingHttpUpgradeService implements Service<RemotingHttpUpgradeService> {


    public static final String JBOSS_REMOTING = "jboss-remoting";

    /**
     * Magic number used in the handshake.
     * <p/>
     * The handshake borrows heavily from the web socket protocol, but uses different header
     * names and a different magic number.
     */
    public static final String MAGIC_NUMBER = "CF70DEB8-70F9-4FBA-8B4F-DFC3E723B4CD";

    //headers
    public static final String SEC_JBOSS_REMOTING_KEY = "Sec-JbossRemoting-Key";
    public static final String SEC_JBOSS_REMOTING_ACCEPT = "Sec-JbossRemoting-Accept";

    /**
     * Base service name for this HTTP Upgrade refist
     */
    public static final ServiceName HTTP_UPGRADE_REGISTRY = ServiceName.JBOSS.append("http-upgrade-registry");
    public static final ServiceName UPGRADE_SERVICE_NAME = ServiceName.JBOSS.append("remoting", "remoting-http-upgrade-service");

    private final String httpConnectorName;
    private final String endpointName;


    private final InjectedValue<ChannelUpgradeHandler> injectedRegistry = new InjectedValue<>();
    private final InjectedValue<ListenerRegistry> listenerRegistry = new InjectedValue<>();
    private final InjectedValue<Endpoint> injectedEndpoint = new InjectedValue<>();
    private final InjectedValue<org.jboss.as.domain.management.SecurityRealm> injectedSecurityRealm = new InjectedValue<>();
    private final InjectedValue<SaslAuthenticationFactory> injectedSaslAuthenticationFactory = new InjectedValue<>();
    private final OptionMap connectorPropertiesOptionMap;

    private ListenerRegistry.HttpUpgradeMetadata httpUpgradeMetadata;

    public RemotingHttpUpgradeService(final String httpConnectorName, final String endpointName, final OptionMap connectorPropertiesOptionMap) {
        this.httpConnectorName = httpConnectorName;
        this.endpointName = endpointName;
        this.connectorPropertiesOptionMap = connectorPropertiesOptionMap;
    }


    public static void installServices(final OperationContext context, final String remotingConnectorName, final String httpConnectorName, final ServiceName endpointName,
            final OptionMap connectorPropertiesOptionMap, final String securityRealm, final String saslAuthenticationFactory) {
        ServiceTarget serviceTarget = context.getServiceTarget();
        final RemotingHttpUpgradeService service = new RemotingHttpUpgradeService(httpConnectorName, endpointName.getSimpleName(), connectorPropertiesOptionMap);

        ServiceBuilder<RemotingHttpUpgradeService> serviceBuilder = serviceTarget.addService(UPGRADE_SERVICE_NAME.append(remotingConnectorName), service)
                .setInitialMode(ServiceController.Mode.PASSIVE)
                .addDependency(HTTP_UPGRADE_REGISTRY.append(httpConnectorName), ChannelUpgradeHandler.class, service.injectedRegistry)
                .addDependency(HttpListenerRegistryService.SERVICE_NAME, ListenerRegistry.class, service.listenerRegistry)
                .addDependency(endpointName, Endpoint.class, service.injectedEndpoint);

        if (securityRealm != null) {
            serviceBuilder.addDependency(
                    org.jboss.as.domain.management.SecurityRealm.ServiceUtil.createServiceName(securityRealm),
                    org.jboss.as.domain.management.SecurityRealm.class, service.injectedSecurityRealm);
        }

        if (saslAuthenticationFactory != null) {
            serviceBuilder.addDependency(
                    context.getCapabilityServiceName(SASL_AUTHENTICATION_FACTORY_CAPABILITY, saslAuthenticationFactory, SaslAuthenticationFactory.class),
                    SaslAuthenticationFactory.class, service.injectedSaslAuthenticationFactory);
        }

        serviceBuilder.install();
    }


    @Override
    public synchronized void start(final StartContext context) throws StartException {
        final Endpoint endpoint = injectedEndpoint.getValue();
        OptionMap.Builder builder = OptionMap.builder();

        ListenerRegistry.Listener listenerInfo = listenerRegistry.getValue().getListener(httpConnectorName);
        assert listenerInfo != null;
        listenerInfo.addHttpUpgradeMetadata(httpUpgradeMetadata = new ListenerRegistry.HttpUpgradeMetadata("jboss-remoting", endpointName));
        RemotingConnectorBindingInfoService.install(context.getChildTarget(), context.getController().getName().getSimpleName(), (SocketBinding)listenerInfo.getContextInformation("socket-binding"), listenerInfo.getProtocol().equals("https") ? REMOTE_HTTPS : REMOTE_HTTP);

        if (connectorPropertiesOptionMap != null) {
            builder.addAll(connectorPropertiesOptionMap);
        }
        OptionMap resultingMap = builder.getMap();
        try {
            final ExternalConnectionProvider provider = endpoint.getConnectionProviderInterface(Protocol.HTTP_REMOTING.toString(), ExternalConnectionProvider.class);

            SaslAuthenticationFactory saslAuthenticationFactory = injectedSaslAuthenticationFactory.getOptionalValue();

            org.jboss.as.domain.management.SecurityRealm securityRealm = null;
            if (saslAuthenticationFactory == null && (securityRealm = injectedSecurityRealm.getOptionalValue()) != null) {

                final ClassLoader loader = WildFlySecurityManager.getClassLoaderPrivileged(ConnectorUtils.class);

                Option<?> optionMechanismNames = Option.fromString("org.xnio.Options."+ Options.SASL_MECHANISMS.getName(), loader);
                String[] mechanismNames = null;
                if(connectorPropertiesOptionMap.contains(optionMechanismNames)) {
                    Object o = connectorPropertiesOptionMap.get(optionMechanismNames);
                    if (o instanceof Sequence) {
                        Sequence<String> sequence = (Sequence<String>) connectorPropertiesOptionMap.get(optionMechanismNames);
                        mechanismNames = sequence.toArray(new String[sequence.size()]);
                    }
                }

                Option<?> optionPolicyNonanonymous = Option.fromString("org.xnio.Options."+ Options.SASL_POLICY_NOANONYMOUS.getName(), loader);
                //in case that legacy sasl mechanisms are used, noanonymous default value is true
                Boolean policyNonanonymous = mechanismNames == null ? null: true;
                if(connectorPropertiesOptionMap.contains(optionPolicyNonanonymous)) {
                    Object o = connectorPropertiesOptionMap.get(optionPolicyNonanonymous);
                    if (o instanceof Boolean) {
                        policyNonanonymous = (Boolean) o;
                    }
                }

                if(mechanismNames != null || policyNonanonymous != null) {
                    saslAuthenticationFactory = securityRealm.getSaslAuthenticationFactory(mechanismNames, policyNonanonymous);
                } else {
                    saslAuthenticationFactory = securityRealm.getSaslAuthenticationFactory();
                }
            }

            if (saslAuthenticationFactory == null) {
                // TODO Elytron Inject the sasl server factory.
                RemotingLogger.ROOT_LOGGER.warn("****** All authentication is ANONYMOUS for " + getClass().getName());
                final SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
                domainBuilder.addRealm("default", SecurityRealm.EMPTY_REALM).build();
                domainBuilder.setDefaultRealmName("default");
                domainBuilder.setPermissionMapper((permissionMappable, roles) -> LoginPermission.getInstance());
                final SaslAuthenticationFactory.Builder authBuilder = SaslAuthenticationFactory.builder();
                authBuilder.setSecurityDomain(domainBuilder.build());
                authBuilder.setFactory(new AnonymousServerFactory());
                authBuilder.setMechanismConfigurationSelector(mechanismInformation -> MechanismConfiguration.EMPTY);

                saslAuthenticationFactory = authBuilder.build();
            }

            final Consumer<StreamConnection> adaptor = provider.createConnectionAdaptor(resultingMap, saslAuthenticationFactory);

            injectedRegistry.getValue().addProtocol(JBOSS_REMOTING, new ChannelListener<StreamConnection>() {
                @Override
                public void handleEvent(final StreamConnection channel) {
                    adaptor.accept(channel);
                    /*if (channel instanceof SslConnection) {
                        adaptor.accept(new AssembledConnectedSslStreamChannel((SslConnection) channel, channel.getSourceChannel(), channel.getSinkChannel()));
                    } else {
                        adaptor.adapt(new AssembledConnectedStreamChannel(channel, channel.getSourceChannel(), channel.getSinkChannel()));
                    }*/
                }
            }, new SimpleHttpUpgradeHandshake(MAGIC_NUMBER, SEC_JBOSS_REMOTING_KEY, SEC_JBOSS_REMOTING_ACCEPT));

        } catch (UnknownURISchemeException e) {
            throw new StartException(e);
        } catch (IOException e) {
            throw new StartException(e);
        }
    }

    @Override
    public synchronized void stop(final StopContext context) {
        listenerRegistry.getValue().getListener(httpConnectorName).removeHttpUpgradeMetadata(httpUpgradeMetadata);
        httpUpgradeMetadata = null;
        injectedRegistry.getValue().removeProtocol(JBOSS_REMOTING);
    }

    @Override
    public synchronized RemotingHttpUpgradeService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }
}
