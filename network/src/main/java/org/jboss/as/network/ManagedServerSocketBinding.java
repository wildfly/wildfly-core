/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Managed {@code ServerSocket} binding, automatically registering itself
 * at the {@code SocketBindingManager} when bound.
 *
 * @author Emanuel Muckenhuber
 */
public class ManagedServerSocketBinding extends ServerSocket implements ManagedBinding {

    private final String name;
    private final SocketBindingManager socketBindings;
    private final boolean metrics;
    private final AtomicLong acceptCount = new AtomicLong(0);

    ManagedServerSocketBinding(final SocketBindingManager socketBindings) throws IOException {
        this(null, socketBindings, false);
    }

    ManagedServerSocketBinding(final SocketBindingManager socketBindings, final boolean metrics) throws IOException {
        this(null, socketBindings, metrics);
    }

    ManagedServerSocketBinding(final String name, final SocketBindingManager socketBindings) throws IOException {
        this(name, socketBindings, false);
    }

    ManagedServerSocketBinding(final String name, final SocketBindingManager socketBindings, final boolean metrics) throws IOException {
        this.name = name;
        this.socketBindings = socketBindings;
        this.metrics = metrics;
    }

    @Override
    public String getSocketBindingName() {
        return name;
    }

    @Override
    public InetSocketAddress getBindAddress() {
        return InetSocketAddress.class.cast(getLocalSocketAddress());
    }

    @Override
    public void bind(SocketAddress endpoint, int backlog) throws IOException {
        super.bind(endpoint, backlog);
        if(name != null) {
            socketBindings.getNamedRegistry().registerBinding(this);
        } else {
            socketBindings.getUnnamedRegistry().registerBinding(this);
        }
    }

    @Override
    public Socket accept() throws IOException {
        final Socket socket = metrics ? new ManagedSocketBinding(socketBindings.getUnnamedRegistry()) : new Socket();
        implAccept(socket);
        if(metrics) {
            acceptCount.incrementAndGet();
        }
        return socket;
    }

    @Override
    public void close() throws IOException {
        try {
            if(name != null) {
                socketBindings.getNamedRegistry().unregisterBinding(this);
            } else {
                socketBindings.getUnnamedRegistry().unregisterBinding(this);
            }
        } finally {
            super.close();
        }
    }

    public long getAcceptCount() {
        return acceptCount.get();
    }

}

