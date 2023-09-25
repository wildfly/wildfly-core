/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.network;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;

/**
 * Managed {@code DatagramSocket} binding, automatically registering itself
 * at the {@code SocketBindingManager} when bound.
 *
 * @author Emanuel Muckenhuber
 */
public class ManagedDatagramSocketBinding extends DatagramSocket implements ManagedBinding {

    private final String name;
    private final SocketAddress address;
    private final ManagedBindingRegistry registry;

    ManagedDatagramSocketBinding(final String name, final ManagedBindingRegistry socketBindings, SocketAddress address) throws SocketException {
        super(address);
        this.name = name;
        this.address = address;
        this.registry = socketBindings;
        if (this.isBound()) {
            this.registry.registerBinding(this);
        }
    }

    @Override
    public String getSocketBindingName() {
        return name;
    }

    @Override
    public InetSocketAddress getBindAddress() {
        if (name == null) {
            // unnamed datagram socket
            return (InetSocketAddress) address;
        } else {
            return (InetSocketAddress) getLocalSocketAddress();
        }
    }

    @Override
    public synchronized void bind(SocketAddress addr) throws SocketException {
        super.bind(addr);
        // This method might have been called from the super constructor
        if (this.registry != null) {
            this.registry.registerBinding(this);
        }
    }

    @Override
    public void close() {
        try {
            // This method might have been called from the super constructor
            if (this.registry != null) {
                registry.unregisterBinding(this);
            }
        } finally {
            super.close();
        }
    }
}

