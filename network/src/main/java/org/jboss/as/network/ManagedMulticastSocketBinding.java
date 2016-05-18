/*
* JBoss, Home of Professional Open Source
* Copyright 2010, Red Hat Inc., and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
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
package org.jboss.as.network;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.SocketException;

/**
 * Managed {@code MulticastSocket} binding, automatically registering itself
 * at the {@code SocketBindingManager} when bound.
 *
 * @author Emanuel Muckenhuber
 */
public class ManagedMulticastSocketBinding extends MulticastSocket implements ManagedBinding {

    static ManagedMulticastSocketBinding create(final String name, final ManagedBindingRegistry socketBindings, SocketAddress address) throws IOException {
        if (NetworkUtils.isBindingToMulticastAddressSupported()) {
            return new ManagedMulticastSocketBinding(name, socketBindings, address);
        } else if (address instanceof InetSocketAddress) {
            return new ManagedMulticastSocketBinding(name, socketBindings, new InetSocketAddress(((InetSocketAddress) address).getPort()));
        } else {
            // Probably non-existing case; only happens if an end-user caller deliberately passes such an
            // address to SocketBindingManager
            return new ManagedMulticastSocketBinding(name, socketBindings, address);
        }
    }

    private final String name;
    private final SocketAddress address;
    private final ManagedBindingRegistry socketBindings;

    private ManagedMulticastSocketBinding(final String name, final ManagedBindingRegistry socketBindings, SocketAddress address) throws IOException {
        super(address);
        this.name = name;
        this.address = address;
        this.socketBindings = socketBindings;
        if (this.isBound()) {
            this.socketBindings.registerBinding(this);
        }
    }

    @Override
    public String getSocketBindingName() {
        return name;
    }

    @Override
    public InetSocketAddress getBindAddress() {
        if (name == null) {
            // unnamed multicast socket
            return (InetSocketAddress) address;
        } else {
            return (InetSocketAddress) getLocalSocketAddress();
        }
    }

    @Override
    public synchronized void bind(SocketAddress addr) throws SocketException {
        super.bind(addr);
        // This method might have been called from the super constructor
        if (this.socketBindings != null) {
            this.socketBindings.registerBinding(this);
        }
    }

    @Override
    public void close() {
        try {
            // This method might have been called from the super constructor
            if (this.socketBindings != null) {
                socketBindings.unregisterBinding(this);
            }
        } finally {
            super.close();
        }
    }
}

