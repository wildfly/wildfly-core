/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * Managed {@code Socket} binding, automatically registering itself at
 * the {@code SocketBindingManager} when bound.
 *
 * @author Emanuel Muckenhuber
 */
class ManagedSocketBinding extends Socket implements ManagedBinding {

    private final String name;
    private final ManagedBindingRegistry socketBindings;

    ManagedSocketBinding(final ManagedBindingRegistry socketBindings) {
        this(null, socketBindings);
    }

    ManagedSocketBinding(final String name, final ManagedBindingRegistry socketBindings) {
        this.name = name;
        this.socketBindings = socketBindings;
    }

    @Override
    public String getSocketBindingName() {
        return name;
    }

    public InetSocketAddress getBindAddress() {
        return new InetSocketAddress(getLocalAddress(), getPort());
    }

    public void bind(SocketAddress bindpoint) throws IOException {
        super.bind(bindpoint);
        socketBindings.registerBinding(this);
    }

    public synchronized void close() throws IOException {
        try {
            socketBindings.unregisterBinding(this);
        } finally {
            super.close();
        }
    }

}

