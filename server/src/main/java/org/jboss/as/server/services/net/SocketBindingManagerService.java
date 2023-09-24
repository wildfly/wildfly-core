/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.services.net;

import java.net.InetAddress;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.as.network.SocketBindingManagerImpl;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;

/**
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class SocketBindingManagerService extends SocketBindingManagerImpl implements Service<SocketBindingManager> {

    private final Consumer<SocketBindingManager> socketBindingManagerConsumer;
    private final Supplier<NetworkInterfaceBinding> networkInterfaceBindingSupplier;
    private final int portOffSet;

    SocketBindingManagerService(final Consumer<SocketBindingManager> socketBindingManagerConsumer,
                                final Supplier<NetworkInterfaceBinding> networkInterfaceBindingSupplier,
                                final int portOffSet) {
        this.socketBindingManagerConsumer = socketBindingManagerConsumer;
        this.networkInterfaceBindingSupplier = networkInterfaceBindingSupplier;
        this.portOffSet = portOffSet;
    }

    @Override
    public void start(final StartContext context) {
        socketBindingManagerConsumer.accept(this);
    }

    @Override
    public void stop(final StopContext context) {
        socketBindingManagerConsumer.accept(null);
    }

    @Override
    public SocketBindingManager getValue() throws IllegalStateException {
        return this;
    }

    @Override
    public int getPortOffset() {
        return portOffSet;
    }

    @Override
    public InetAddress getDefaultInterfaceAddress() {
        return networkInterfaceBindingSupplier.get().getAddress();
    }

    @Override
    public NetworkInterfaceBinding getDefaultInterfaceBinding() {
        return networkInterfaceBindingSupplier.get();
    }

}
