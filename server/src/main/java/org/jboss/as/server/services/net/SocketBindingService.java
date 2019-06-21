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
package org.jboss.as.server.services.net;

import java.net.InetAddress;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.network.ClientMapping;
import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;

/**
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class SocketBindingService implements Service<SocketBinding> {

    private final String name;
    private final int port;
    private final boolean isFixedPort;
    private final InetAddress multicastAddress;
    private final int multicastPort;
    private final List<ClientMapping> clientMappings;

    /** The created binding. */
    private SocketBinding binding;

    private final Consumer<SocketBinding> socketBindingConsumer;
    private final Supplier<NetworkInterfaceBinding> interfaceBindingSupplier;
    private final Supplier<SocketBindingManager> socketBindingsSupplier;

    public SocketBindingService(final Consumer<SocketBinding> socketBindingConsumer,
                                final Supplier<NetworkInterfaceBinding> interfaceBindingSupplier,
                                final Supplier<SocketBindingManager> socketBindingsSupplier,
                                final String name, int port, boolean isFixedPort,
                                InetAddress multicastAddress, int multicastPort,
                                List<ClientMapping> clientMappings) {
        assert name != null : "name is null";
        this.socketBindingConsumer = socketBindingConsumer;
        this.interfaceBindingSupplier = interfaceBindingSupplier;
        this.socketBindingsSupplier = socketBindingsSupplier;
        this.name = name;
        this.port = port;
        this.isFixedPort = isFixedPort;
        this.multicastAddress = multicastAddress;
        this.multicastPort = multicastPort;
        this.clientMappings = clientMappings;
    }

    @Override
    public synchronized void start(final StartContext context) {
        binding = new SocketBinding(name, port, isFixedPort,
           multicastAddress, multicastPort,
           interfaceBindingSupplier != null ? interfaceBindingSupplier.get() : null,
           socketBindingsSupplier.get(), clientMappings);
        socketBindingConsumer.accept(binding);
    }

    @Override
    public synchronized void stop(final StopContext context) {
        socketBindingConsumer.accept(null);
        binding = null;
    }

    @Override
    public synchronized SocketBinding getValue() throws IllegalStateException {
        final SocketBinding binding = this.binding;
        if (binding == null) {
            throw new IllegalStateException();
        }
        return binding;
    }

}
