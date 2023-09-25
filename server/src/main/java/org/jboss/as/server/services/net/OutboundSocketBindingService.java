/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.services.net;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;

/**
 * Service that represents an outbound socket binding
 *
 * @author Jaikiran Pai
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public abstract class OutboundSocketBindingService implements Service<OutboundSocketBinding> {

    protected final String outboundSocketName;
    protected final Integer sourcePort;
    private final Consumer<OutboundSocketBinding> outboundSocketBindingConsumer;
    protected final Supplier<SocketBindingManager> socketBindingManagerSupplier;
    protected final Supplier<NetworkInterfaceBinding> sourceInterfaceSupplier;
    protected final boolean fixedSourcePort;

    private volatile OutboundSocketBinding outboundSocketBinding;

    public OutboundSocketBindingService(final Consumer<OutboundSocketBinding> outboundSocketBindingConsumer,
                                        final Supplier<SocketBindingManager> socketBindingManagerSupplier,
                                        final Supplier<NetworkInterfaceBinding> sourceInterfaceSupplier,
                                        final String name, final Integer sourcePort, final boolean fixedSourcePort) {
        this.outboundSocketBindingConsumer = outboundSocketBindingConsumer;
        this.socketBindingManagerSupplier = socketBindingManagerSupplier;
        this.sourceInterfaceSupplier = sourceInterfaceSupplier;
        this.outboundSocketName = name;
        this.sourcePort = sourcePort;
        this.fixedSourcePort = fixedSourcePort;
    }

    @Override
    public synchronized void start(final StartContext context) {
        outboundSocketBinding = this.createOutboundSocketBinding();
        outboundSocketBindingConsumer.accept(outboundSocketBinding);
    }

    @Override
    public synchronized void stop(final StopContext context) {
        outboundSocketBindingConsumer.accept(null);
        outboundSocketBinding = null;
    }

    @Override
    public synchronized OutboundSocketBinding getValue() throws IllegalStateException, IllegalArgumentException {
        return this.outboundSocketBinding;
    }

    /**
     * Create and return the {@link OutboundSocketBinding} for this outbound socket binding service
     * @return
     */
    protected abstract OutboundSocketBinding createOutboundSocketBinding();
}
