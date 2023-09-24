/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.network;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * A representation of a socket binding. Open ports need to be registered
 * using the {@code SocketBindingManager}.
 *
 * @author Emanuel Muckenhuber
 */
public interface ManagedBinding extends Closeable {

    /**
     * Get the optional socket binding configuration name.
     *
     * @return the socket binding name, <code>null</code> if not available
     */
    String getSocketBindingName();

    /**
     * Get the bind address.
     *
     * @return the bind address.
     */
    InetSocketAddress getBindAddress();

    /**
     * Close and unregister this binding.
     *
     * @throws IOException if an I/O error occurs
     */
    void close() throws IOException;

    final class Factory {
        public static ManagedBinding createSimpleManagedBinding(final String name, final InetSocketAddress socketAddress, final Closeable closeable) {
            checkNotNullParam("socketAddress", socketAddress);

            return new ManagedBinding() {

                @Override
                public String getSocketBindingName() {
                    return name;
                }

                @Override
                public InetSocketAddress getBindAddress() {
                    return socketAddress;
                }

                @Override
                public void close() throws IOException {
                    if (closeable != null) {
                        closeable.close();
                    }
                }
            };
        }

        public static ManagedBinding createSimpleManagedBinding(final SocketBinding socketBinding) {
            checkNotNullParam("socketBinding", socketBinding);

            return new ManagedBinding() {

                @Override
                public String getSocketBindingName() {
                    return socketBinding.getName();
                }

                @Override
                public InetSocketAddress getBindAddress() {
                    return socketBinding.getSocketAddress();
                }

                @Override
                public void close() throws IOException {
                    // no-op
                }
            };
        }
    }

}

