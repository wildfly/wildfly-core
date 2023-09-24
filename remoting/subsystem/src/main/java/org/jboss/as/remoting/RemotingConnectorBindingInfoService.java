/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import static org.jboss.as.remoting.ConnectorResource.CONNECTOR_CAPABILITY;

import org.jboss.as.network.ProtocolSocketBinding;
import org.jboss.as.network.SocketBinding;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;

import java.util.function.Consumer;

/**
 * Service that publishes socket binding information for remoting connectors
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class RemotingConnectorBindingInfoService implements Service {

    /** Deprecated. Use the 'org.wildfly.remoting.connector' capability */
    @Deprecated
    private static final ServiceName SERVICE_NAME = RemotingServices.REMOTING_BASE.append("remotingConnectorInfoService");

    private final Consumer<ProtocolSocketBinding> serviceConsumer;
    private final Consumer<RemotingConnectorInfo> legacyServiceConsumer;
    private final ProtocolSocketBinding binding;

    private RemotingConnectorBindingInfoService(final Consumer<ProtocolSocketBinding> serviceConsumer,
                                                final Consumer<RemotingConnectorInfo> legacyServiceConsumer,
                                                final ProtocolSocketBinding binding) {
        this.serviceConsumer = serviceConsumer;
        this.legacyServiceConsumer = legacyServiceConsumer;
        this.binding = binding;
    }

    /** Deprecated. Use a {@link ProtocolSocketBinding} provided by the {@code org.wildfly.remoting.connector} capability */
    @Deprecated
    public static ServiceName serviceName(final String connectorName) {
        return SERVICE_NAME.append(connectorName);
    }

    public static void install(final ServiceTarget target, final String connectorName, final SocketBinding binding, final Protocol protocol) {
        final ServiceName serviceName = CONNECTOR_CAPABILITY.getCapabilityServiceName(connectorName);
        final ServiceBuilder<?> sb = target.addService(serviceName);
        final Consumer<ProtocolSocketBinding> serviceConsumer = sb.provides(serviceName);
        final Consumer<RemotingConnectorInfo> legacyServiceConsumer = sb.provides(serviceName(connectorName));
        final ProtocolSocketBinding protocolSocketBinding = new ProtocolSocketBinding(protocol.toString(), binding);
        sb.setInstance(new RemotingConnectorBindingInfoService(serviceConsumer, legacyServiceConsumer, protocolSocketBinding));
        sb.install();
    }

    @Override
    public void start(final StartContext startContext) {
        serviceConsumer.accept(binding);
        legacyServiceConsumer.accept(new RemotingConnectorInfo(binding));
    }

    @Override
    public void stop(final StopContext stopContext) {
        serviceConsumer.accept(null);
        legacyServiceConsumer.accept(null);
    }

    /** @deprecated Use {@link ProtocolSocketBinding} */
    @Deprecated
    public static final class RemotingConnectorInfo {
        private final ProtocolSocketBinding protocolSocketBinding;

        /** @deprecated there is no need for a public constructor so it will be removed */
        @Deprecated
        public RemotingConnectorInfo(SocketBinding socketBinding, Protocol protocol) {
            this(new ProtocolSocketBinding(protocol.toString(), socketBinding));
        }

        private RemotingConnectorInfo(ProtocolSocketBinding protocolSocketBinding) {
            this.protocolSocketBinding = protocolSocketBinding;
        }

        public SocketBinding getSocketBinding() {
            return protocolSocketBinding.getSocketBinding();
        }

        public String getProtocol() {
            return protocolSocketBinding.getProtocol();
        }
    }

}
