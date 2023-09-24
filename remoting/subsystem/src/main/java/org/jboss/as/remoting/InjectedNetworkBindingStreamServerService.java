/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import java.net.InetSocketAddress;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.network.ManagedBinding;
import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.remoting3.Endpoint;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.xnio.OptionMap;
import org.xnio.StreamConnection;
import org.xnio.channels.AcceptingChannel;

import javax.net.ssl.SSLContext;

/**
 * {@link AbstractStreamServerService} that uses an injected network interface binding service.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class InjectedNetworkBindingStreamServerService extends AbstractStreamServerService {

    private final Supplier<NetworkInterfaceBinding> interfaceBindingSupplier;
    private final int port;

    InjectedNetworkBindingStreamServerService(
            final Consumer<AcceptingChannel<StreamConnection>> streamServerConsumer,
            final Supplier<Endpoint> endpointSupplier,
            final Supplier<SaslAuthenticationFactory> saslAuthenticationFactorySupplier,
            final Supplier<SSLContext> sslContextSupplier,
            final Supplier<SocketBindingManager> socketBindingManagerSupplier,
            final Supplier<NetworkInterfaceBinding> interfaceBindingSupplier,
            final OptionMap connectorPropertiesOptionMap, int port) {
        super(streamServerConsumer, endpointSupplier, saslAuthenticationFactorySupplier,
                sslContextSupplier, socketBindingManagerSupplier, connectorPropertiesOptionMap);
        this.interfaceBindingSupplier = interfaceBindingSupplier;
        this.port = port;
    }

    @Override
    InetSocketAddress getSocketAddress() {
        return new InetSocketAddress(interfaceBindingSupplier.get().getAddress(), port);
    }

    @Override
    ManagedBinding registerSocketBinding(SocketBindingManager socketBindingManager) {
        InetSocketAddress address = new InetSocketAddress(interfaceBindingSupplier.get().getAddress(), port);
        ManagedBinding binding = ManagedBinding.Factory.createSimpleManagedBinding("management-native", address, null);
        socketBindingManager.getUnnamedRegistry().registerBinding(binding);
        return binding;
    }

    @Override
    void unregisterSocketBinding(ManagedBinding managedBinding, SocketBindingManager socketBindingManager) {
        socketBindingManager.getUnnamedRegistry().unregisterBinding(managedBinding);
    }

}
