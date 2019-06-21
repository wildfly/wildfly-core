/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
