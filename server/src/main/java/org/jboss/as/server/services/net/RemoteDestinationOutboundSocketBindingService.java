/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.services.net;

import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.as.network.SocketBindingManager;
import org.wildfly.common.Assert;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Service that represents a remote-destination outbound socket binding
 *
 * @author Jaikiran Pai
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class RemoteDestinationOutboundSocketBindingService extends OutboundSocketBindingService {

    private final String destinationHost;
    private final int destinationPort;

    public RemoteDestinationOutboundSocketBindingService(final Consumer<OutboundSocketBinding> outboundSocketBindingConsumer,
                                                         final Supplier<SocketBindingManager> socketBindingManagerSupplier,
                                                         final Supplier<NetworkInterfaceBinding> sourceInterfaceSupplier,
                                                         final String name, final String destinationAddress, final int destinationPort,
                                                         final Integer sourcePort, final boolean fixedSourcePort) {

        super(outboundSocketBindingConsumer, socketBindingManagerSupplier, sourceInterfaceSupplier, name, sourcePort, fixedSourcePort);
        Assert.checkNotNullParam("destinationAddress", destinationAddress);
        Assert.checkNotEmptyParam("destinationAddress", destinationAddress);
        Assert.checkMinimumParameter("destinationPort", 0, destinationPort);
        this.destinationHost = destinationAddress;
        this.destinationPort = destinationPort;
    }

    @Override
    protected OutboundSocketBinding createOutboundSocketBinding() {
        return new OutboundSocketBinding(outboundSocketName, socketBindingManagerSupplier.get(), destinationHost, destinationPort,
                sourceInterfaceSupplier != null ? sourceInterfaceSupplier.get() : null, sourcePort, fixedSourcePort);
    }
}
