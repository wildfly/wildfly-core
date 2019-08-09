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
import java.util.function.Supplier;

import io.undertow.server.ListenerRegistry;
import io.undertow.server.handlers.ChannelUpgradeHandler;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.domain.management.security.SecurityRealmService;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.remoting.logging.RemotingLogger;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.UnknownURISchemeException;
import org.jboss.remoting3.spi.ExternalConnectionProvider;
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
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class RemotingHttpUpgradeService implements Service {

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


    private final Consumer<RemotingHttpUpgradeService> serviceConsumer;
    private final Supplier<ChannelUpgradeHandler> upgradeRegistrySupplier;
    private final Supplier<ListenerRegistry> listenerRegistrySupplier;
    private final Supplier<Endpoint> endpointSupplier;
    private final Supplier<org.jboss.as.domain.management.SecurityRealm> securityRealmSupplier;
    private final Supplier<SaslAuthenticationFactory> saslAuthenticationFactorySupplier;
    private final OptionMap connectorPropertiesOptionMap;

    private ListenerRegistry.HttpUpgradeMetadata httpUpgradeMetadata;

    public RemotingHttpUpgradeService(final Consumer<RemotingHttpUpgradeService> serviceConsumer,
                                      final Supplier<ChannelUpgradeHandler> upgradeRegistrySupplier,
                                      final Supplier<ListenerRegistry> listenerRegistrySupplier,
                                      final Supplier<Endpoint> endpointSupplier,
                                      final Supplier<org.jboss.as.domain.management.SecurityRealm> securityRealmSupplier,
                                      final Supplier<SaslAuthenticationFactory> saslAuthenticationFactorySupplier,
                                      final String httpConnectorName, final String endpointName, final OptionMap connectorPropertiesOptionMap) {
        this.serviceConsumer = serviceConsumer;
        this.upgradeRegistrySupplier = upgradeRegistrySupplier;
        this.listenerRegistrySupplier = listenerRegistrySupplier;
        this.endpointSupplier = endpointSupplier;
        this.securityRealmSupplier = securityRealmSupplier;
        this.saslAuthenticationFactorySupplier = saslAuthenticationFactorySupplier;
        this.httpConnectorName = httpConnectorName;
        this.endpointName = endpointName;
        this.connectorPropertiesOptionMap = connectorPropertiesOptionMap;
    }

    public static void installServices(final OperationContext context, final String remotingConnectorName,
                                       final String httpConnectorName, final ServiceName endpointName,
                                       final OptionMap connectorPropertiesOptionMap, final String securityRealm,
                                       final String saslAuthenticationFactory) {
        final ServiceTarget serviceTarget = context.getServiceTarget();
        final ServiceName serviceName = UPGRADE_SERVICE_NAME.append(remotingConnectorName);
        final ServiceBuilder<?> sb = serviceTarget.addService(serviceName);
        final Consumer<RemotingHttpUpgradeService> serviceConsumer = sb.provides(serviceName);
        final Supplier<ChannelUpgradeHandler> urSupplier = sb.requires(HTTP_UPGRADE_REGISTRY.append(httpConnectorName));
        final Supplier<ListenerRegistry> lrSupplier = sb.requires(RemotingServices.HTTP_LISTENER_REGISTRY);
        final Supplier<Endpoint> eSupplier = sb.requires(endpointName);
        final Supplier<org.jboss.as.domain.management.SecurityRealm> srSupplier = securityRealm != null ? sb.requires(org.jboss.as.domain.management.SecurityRealm.ServiceUtil.createServiceName(securityRealm)) : null;
        final Supplier<SaslAuthenticationFactory> safSupplier = saslAuthenticationFactory != null ? sb.requires(context.getCapabilityServiceName(SASL_AUTHENTICATION_FACTORY_CAPABILITY, saslAuthenticationFactory, SaslAuthenticationFactory.class)) : null;
        sb.setInstance(new RemotingHttpUpgradeService(serviceConsumer, urSupplier, lrSupplier, eSupplier, srSupplier, safSupplier, httpConnectorName, endpointName.getSimpleName(), connectorPropertiesOptionMap));
        sb.setInitialMode(ServiceController.Mode.PASSIVE);
        sb.install();
    }


    @Override
    public synchronized void start(final StartContext context) throws StartException {
        final Endpoint endpoint = endpointSupplier.get();
        OptionMap.Builder builder = OptionMap.builder();

        ListenerRegistry.Listener listenerInfo = listenerRegistrySupplier.get().getListener(httpConnectorName);
        assert listenerInfo != null;
        listenerInfo.addHttpUpgradeMetadata(httpUpgradeMetadata = new ListenerRegistry.HttpUpgradeMetadata("jboss-remoting", endpointName));
        RemotingConnectorBindingInfoService.install(context.getChildTarget(), context.getController().getName().getSimpleName(), (SocketBinding)listenerInfo.getContextInformation("socket-binding"), listenerInfo.getProtocol().equals("https") ? REMOTE_HTTPS : REMOTE_HTTP);

        if (connectorPropertiesOptionMap != null) {
            builder.addAll(connectorPropertiesOptionMap);
        }
        OptionMap resultingMap = builder.getMap();
        try {
            final ExternalConnectionProvider provider = endpoint.getConnectionProviderInterface(Protocol.HTTP_REMOTING.toString(), ExternalConnectionProvider.class);

            SaslAuthenticationFactory saslAuthenticationFactory = saslAuthenticationFactorySupplier != null ? saslAuthenticationFactorySupplier.get() : null;

            org.jboss.as.domain.management.SecurityRealm securityRealm = null;
            if (saslAuthenticationFactory == null && (securityRealm = securityRealmSupplier != null ? securityRealmSupplier.get() : null) != null) {

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
                domainBuilder.setPermissionMapper((permissionMappable, roles) -> SecurityRealmService.createPermissionVerifier());
                final SaslAuthenticationFactory.Builder authBuilder = SaslAuthenticationFactory.builder();
                authBuilder.setSecurityDomain(domainBuilder.build());
                authBuilder.setFactory(new AnonymousServerFactory());
                authBuilder.setMechanismConfigurationSelector(mechanismInformation -> MechanismConfiguration.EMPTY);

                saslAuthenticationFactory = authBuilder.build();
            }

            final Consumer<StreamConnection> adaptor = provider.createConnectionAdaptor(resultingMap, saslAuthenticationFactory);

            upgradeRegistrySupplier.get().addProtocol(JBOSS_REMOTING, new ChannelListener<StreamConnection>() {
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
            serviceConsumer.accept(this);
        } catch (UnknownURISchemeException e) {
            throw new StartException(e);
        } catch (IOException e) {
            throw new StartException(e);
        }
    }

    @Override
    public synchronized void stop(final StopContext context) {
        serviceConsumer.accept(null);
        listenerRegistrySupplier.get().getListener(httpConnectorName).removeHttpUpgradeMetadata(httpUpgradeMetadata);
        httpUpgradeMetadata = null;
        upgradeRegistrySupplier.get().removeProtocol(JBOSS_REMOTING);
    }

}
