/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.services.net;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.network.SocketBindingManager;

/**
 * Service that represents a local-destination outbound socket binding
 *
 * @author Jaikiran Pai
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class LocalDestinationOutboundSocketBindingService extends OutboundSocketBindingService {

    private final Supplier<SocketBinding> localDestinationSocketBindingSupplier;

    public LocalDestinationOutboundSocketBindingService(final Consumer<OutboundSocketBinding> outboundSocketBindingConsumer,
                                                        final Supplier<SocketBindingManager> socketBindingManagerSupplier,
                                                        final Supplier<NetworkInterfaceBinding> sourceInterfaceSupplier,
                                                        final Supplier<SocketBinding> localDestinationSocketBindingSupplier,
                                                        final String name, final Integer sourcePort, final boolean fixedSourcePort) {
        super(outboundSocketBindingConsumer, socketBindingManagerSupplier, sourceInterfaceSupplier, name, sourcePort, fixedSourcePort);
        this.localDestinationSocketBindingSupplier = localDestinationSocketBindingSupplier;
    }

    @Override
    protected OutboundSocketBinding createOutboundSocketBinding() {
        // unlike the RemoteDestinationOutboundSocketBindingService, we resolve the destination address
        // here itself (instead of doing it lazily in the OutboundSocketBinding) is because we already
        // inject a SocketBinding reference which is local to the server instance and is expected to have
        // already resolved the (local) address (via the NetworkInterfaceBinding).
        final InetAddress destinationAddress = this.getDestinationAddress();
        final int destinationPort = this.getDestinationPort();
        return new OutboundSocketBinding(outboundSocketName, socketBindingManagerSupplier.get(),
                destinationAddress, destinationPort, sourceInterfaceSupplier != null ? sourceInterfaceSupplier.get() : null,
                sourcePort, fixedSourcePort);
    }

    private InetAddress getDestinationAddress() {
        final SocketBinding localDestinationSocketBinding = localDestinationSocketBindingSupplier.get();
        return localDestinationSocketBinding.getSocketAddress().getAddress();
    }

    private int getDestinationPort() {
        final SocketBinding localDestinationSocketBinding = localDestinationSocketBindingSupplier.get();
        // instead of directly using SocketBinding.getPort(), we go via the SocketBinding.getSocketAddress()
        // since the getPort() method doesn't take into account whether the port is a fixed port or whether an offset
        // needs to be added. Alternatively, we could introduce a getAbsolutePort() in the SocketBinding class.
        final InetSocketAddress socketAddress = localDestinationSocketBinding.getSocketAddress();
        return socketAddress.getPort();
    }

}
