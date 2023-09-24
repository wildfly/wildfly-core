/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.network;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NetworkChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.wildfly.common.Assert;

/**
 * @author Emanuel Muckenhuber
 */
public abstract class SocketBindingManagerImpl implements SocketBindingManager {

    private final ManagedSocketFactory socketFactory = new ManagedSocketFactoryImpl();
    private final ManagedServerSocketFactory serverSocketFactory = new ManagedServerSocketFactoryImpl();

    private final NamedManagedBindingRegistry namedRegistry = new NamedRegistryImpl();
    private final UnnamedBindingRegistry unnamedRegistry = new UnnamedRegistryImpl();

    /** {@inheritDoc} */
    @Override
    public ManagedServerSocketFactory getServerSocketFactory() {
        return serverSocketFactory;
    }

    /** {@inheritDoc} */
    @Override
    public ManagedSocketFactory getSocketFactory() {
        return socketFactory;
    }

    /** {@inheritDoc} */
    @Override
    public DatagramSocket createDatagramSocket(String name) throws SocketException {
        Assert.checkNotNullParam("name", name);
        return new ManagedDatagramSocketBinding(name, this.namedRegistry, null);
    }

    /** {@inheritDoc} */
    @Override
    public DatagramSocket createDatagramSocket() throws SocketException {
        return new ManagedDatagramSocketBinding(null, this.unnamedRegistry, null);
    }

    /** {@inheritDoc} */
    @Override
    public DatagramSocket createDatagramSocket(String name, SocketAddress address) throws SocketException {
        Assert.checkNotNullParam("name", name);
        Assert.checkNotNullParam("address", address);
        return new ManagedDatagramSocketBinding(name, this.namedRegistry, address);
    }

    /** {@inheritDoc} */
    @Override
    public DatagramSocket createDatagramSocket(SocketAddress address) throws SocketException {
        Assert.checkNotNullParam("address", address);
        return new ManagedDatagramSocketBinding(null, this.unnamedRegistry, address);
    }

    /** {@inheritDoc} */
    @Override
    public MulticastSocket createMulticastSocket(String name) throws IOException {
        Assert.checkNotNullParam("name", name);
        return ManagedMulticastSocketBinding.create(name, this.namedRegistry, null);
    }

    /** {@inheritDoc} */
    @Override
    public MulticastSocket createMulticastSocket() throws IOException {
        return ManagedMulticastSocketBinding.create(null, this.unnamedRegistry, null);
    }

    /** {@inheritDoc} */
    @Override
    public MulticastSocket createMulticastSocket(String name, SocketAddress address) throws IOException {
        Assert.checkNotNullParam("name", name);
        Assert.checkNotNullParam("address", address);
        return ManagedMulticastSocketBinding.create(name, this.namedRegistry, address);
    }

    /** {@inheritDoc} */
    @Override
    public MulticastSocket createMulticastSocket(SocketAddress address) throws IOException {
        Assert.checkNotNullParam("address", address);
        return ManagedMulticastSocketBinding.create(null, this.unnamedRegistry, address);
    }

    /** {@inheritDoc} */
    @Override
    public NamedManagedBindingRegistry getNamedRegistry() {
        return namedRegistry;
    }

    /** {@inheritDoc} */
    @Override
    public UnnamedBindingRegistry getUnnamedRegistry() {
        return unnamedRegistry;
    }

    private class ManagedSocketFactoryImpl extends ManagedSocketFactory {

        @Override
        public Socket createSocket() {
            return new ManagedSocketBinding(SocketBindingManagerImpl.this.unnamedRegistry);
        }

        @Override
        public Socket createSocket(final String host, final int port) throws IOException {
            return createSocket(InetAddress.getByName(host), port);
        }

        @Override
        public Socket createSocket(final InetAddress host, final int port) throws IOException {
            final Socket socket = createSocket();
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }

        @Override
        public Socket createSocket(final String host, final int port, final InetAddress localHost, final int localPort) throws IOException {
            return createSocket(InetAddress.getByName(host), port, localHost, localPort);
        }

        @Override
        public Socket createSocket(final InetAddress address, final int port, final InetAddress localAddress, final int localPort) throws IOException {
            final Socket socket = createSocket();
            socket.bind(new InetSocketAddress(localAddress, localPort));
            socket.connect(new InetSocketAddress(address, port));
            return socket;
        }

        @Override
        public Socket createSocket(final String name) {
            return new ManagedSocketBinding(name, SocketBindingManagerImpl.this.namedRegistry);
        }

        @Override
        public Socket createSocket(final String name, final String host, final int port) throws IOException {
            return createSocket(name, InetAddress.getByName(host), port);
        }

        @Override
        public Socket createSocket(final String name, final InetAddress host, final int port) throws IOException {
            final Socket socket = createSocket(name);
            socket.connect(new InetSocketAddress(host, port));
            return socket;
        }

        @Override
        public Socket createSocket(final String name, final String host, final int port, final InetAddress localHost, final int localPort) throws IOException {
            return createSocket(name, InetAddress.getByName(host), port, localHost, localPort);
        }

        @Override
        public Socket createSocket(final String name, final InetAddress address, final int port, final InetAddress localAddress, final int localPort) throws IOException {
            final Socket socket = createSocket(name);
            socket.bind(new InetSocketAddress(localAddress, localPort));
            socket.connect(new InetSocketAddress(address, port));
            return socket;
        }

    }

    private class ManagedServerSocketFactoryImpl extends ManagedServerSocketFactory {

        @Override
        public ServerSocket createServerSocket(String name) throws IOException {
            return new ManagedServerSocketBinding(name, SocketBindingManagerImpl.this);
        }
        @Override
        public ServerSocket createServerSocket() throws IOException {
            return createServerSocket(null);
        }
        @Override
        public ServerSocket createServerSocket(int port) throws IOException {
            return createServerSocket(null, port);
        }

        @Override
        public ServerSocket createServerSocket(String name, final int port) throws IOException {
            final ServerSocket serverSocket = createServerSocket(name);
            serverSocket.bind(new InetSocketAddress(port));
            return serverSocket;
        }

        @Override
        public ServerSocket createServerSocket(final int port, final int backlog) throws IOException {
            return createServerSocket(null, port, backlog);
        }

        @Override
        public ServerSocket createServerSocket(String name, int port, int backlog) throws IOException {
            final ServerSocket serverSocket = createServerSocket(name);
            serverSocket.bind(new InetSocketAddress(port), backlog);
            return serverSocket;
        }

        @Override
        public ServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
            return createServerSocket(null, port, backlog, ifAddress);
        }

        @Override
        public ServerSocket createServerSocket(final String name, final int port, final int backlog, final InetAddress ifAddress) throws IOException {
            final ServerSocket serverSocket = createServerSocket(name);
            serverSocket.bind(new InetSocketAddress(ifAddress, port), backlog);
            return serverSocket;
        }

    }

    /**
     * Base class for internal ManagedBinding implementations that
     * 'wrap' some other object in order to provide the ManagedBinding contract.
     * Implementations of this interface can be used as keys in a hash map or
     * values in a hash set and other instances of the same subclass that have
     * the same name or wrap the same object will be treated as equivalent by the map/set.
     *
     * The equals and hashCode implementations of this class are based either on the binding
     * name *or* on the wrapped object, but not on the combination. If a binding has a name,
     * that will be used; otherwise the wrapped object will be used. Both getSocketBindingName()
     * and getWrappedObject() must always provide the same object.
     */
    private abstract static class WrapperBinding implements ManagedBinding {

        /** Gets the wrapped object that provide identity for this binding. */
        abstract Object getWrappedObject();

        @Override
        public final int hashCode() {
            String name = getSocketBindingName();
            return name != null ? name.hashCode() : System.identityHashCode(getWrappedObject());
        }

        @Override
        public final boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            WrapperBinding that = (WrapperBinding) o;
            String name = getSocketBindingName();
            if (name != null) {
                return name.equals(that.getSocketBindingName());
            }

            return getWrappedObject() == that.getWrappedObject();
        }
    }

    private static class NetworkChannelManagedBinding extends WrapperBinding {
        private final String name;
        private final NetworkChannel channel;
        private final ManagedBindingRegistry registry;

        NetworkChannelManagedBinding(final NetworkChannel channel, final ManagedBindingRegistry registry) {
            this(null, channel, registry);
        }

        NetworkChannelManagedBinding(final String name, NetworkChannel channel, final ManagedBindingRegistry registry) {
            assert channel != null;
            this.name = name;
            this.channel = channel;
            this.registry = registry;
        }

        @Override
        public String getSocketBindingName() {
            return name;
        }

        @Override
        public InetSocketAddress getBindAddress() {
            try {
                return (InetSocketAddress) channel.getLocalAddress();
            } catch (ClosedChannelException e) {
                registry.unregisterBinding(this);
                return null;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void close() throws IOException {
            try {
                registry.unregisterBinding(this);
            } finally {
                channel.close();
            }
        }

        @Override
        Object getWrappedObject() {
            return channel;
        }
    }

    private static class WrappedManagedDatagramSocket extends WrapperBinding {
        private final String name;
        private final DatagramSocket socket;
        private final ManagedBindingRegistry registry;
        public WrappedManagedDatagramSocket(final DatagramSocket socket, final ManagedBindingRegistry registry) {
            this(null, socket, registry);
        }
        public WrappedManagedDatagramSocket(final String name, final DatagramSocket socket, final ManagedBindingRegistry registry) {
            assert socket != null;
            this.name = name;
            this.socket = socket;
            this.registry = registry;
        }
        /** {@inheritDoc} */
        @Override
        public String getSocketBindingName() {
            return name;
        }
        @Override
        public InetSocketAddress getBindAddress() {
            return (InetSocketAddress) socket.getLocalSocketAddress();
        }
        @Override
        public void close() throws IOException {
            try {
                registry.unregisterBinding(this);
            } finally {
                socket.close();
            }
        }

        @Override
        Object getWrappedObject() {
            return socket;
        }
    }

    private static class WrappedManagedBinding extends WrapperBinding {
        private final ManagedBinding wrapped;
        private final ManagedBindingRegistry registry;
        public WrappedManagedBinding(final ManagedBinding wrapped, final ManagedBindingRegistry registry) {
            assert wrapped != null;
            this.wrapped = wrapped;
            this.registry = registry;
        }
        @Override
        public String getSocketBindingName() {
            return wrapped.getSocketBindingName();
        }
        @Override
        public InetSocketAddress getBindAddress() {
            return wrapped.getBindAddress();
        }
        @Override
        public void close() throws IOException {
            try {
                registry.unregisterBinding(this);
            } finally {
                wrapped.close();
            }
        }

        @Override
        Object getWrappedObject() {
            return wrapped;
        }
    }

    private static class WrappedManagedSocket extends WrapperBinding {
        private final String name;
        private final Socket socket;
        private final ManagedBindingRegistry registry;
        public WrappedManagedSocket(final Socket socket, final ManagedBindingRegistry registry) {
            this(null, socket, registry);
        }
        public WrappedManagedSocket(final String name, final Socket socket, final ManagedBindingRegistry registry) {
            assert socket != null;
            this.name = name;
            this.socket = socket;
            this.registry = registry;
        }
        @Override
        public String getSocketBindingName() {
            return name;
        }
        @Override
        public InetSocketAddress getBindAddress() {
            return (InetSocketAddress) socket.getLocalSocketAddress();
        }
        @Override
        public void close() throws IOException {
            try {
                registry.unregisterBinding(this);
            } finally {
                socket.close();
            }
        }

        @Override
        Object getWrappedObject() {
            return socket;
        }
    }

    private static class WrappedManagedServerSocket extends WrapperBinding {
        private final String name;
        private final ServerSocket socket;
        private final ManagedBindingRegistry registry;
        public WrappedManagedServerSocket(final ServerSocket socket, final ManagedBindingRegistry registry) {
            this(null, socket, registry);
        }
        public WrappedManagedServerSocket(final String name, final ServerSocket socket, final ManagedBindingRegistry registry) {
            assert socket != null;
            this.name = name;
            this.socket = socket;
            this.registry = registry;
        }
        @Override
        public String getSocketBindingName() {
            return name;
        }
        @Override
        public InetSocketAddress getBindAddress() {
            return (InetSocketAddress) socket.getLocalSocketAddress();
        }
        @Override
        public void close() throws IOException {
            try {
                registry.unregisterBinding(this);
            } finally {
                socket.close();
            }
        }

        @Override
        Object getWrappedObject() {
            return socket;
        }
    }

    private static final class NamedRegistryImpl implements NamedManagedBindingRegistry {
        private final Map<String, ManagedBinding> bindings = new ConcurrentHashMap<String, ManagedBinding>();

        /** {@inheritDoc} */
        @Override
        public ManagedBinding getManagedBinding(String name) {
            return bindings.get(name);
        }

        /** {@inheritDoc} */
        @Override
        public boolean isRegistered(String name) {
            return bindings.containsKey(name);
        }

        /** {@inheritDoc} */
        @Override
        public void registerBinding(ManagedBinding binding) {
            final String name = binding.getSocketBindingName();
            if(name == null) {
                throw new IllegalStateException();
            }
            bindings.put(name, new WrappedManagedBinding(binding, this));
        }

        /** {@inheritDoc} */
        @Override
        public void unregisterBinding(ManagedBinding binding) {
            final String name = binding.getSocketBindingName();
            if(name == null) {
                throw new IllegalStateException();
            }
            unregisterBinding(name);
        }

        /** {@inheritDoc} */
        @Override
        public Collection<ManagedBinding> listActiveBindings() {
            return new HashSet<ManagedBinding>(bindings.values());
        }

        /** {@inheritDoc} */
        @Override
        public Closeable registerSocket(String name, Socket socket) {
            final ManagedBinding binding = new WrappedManagedSocket(name, socket, this);
            registerBinding(binding);
            return binding;
        }

        /** {@inheritDoc} */
        @Override
        public Closeable registerSocket(String name, ServerSocket socket) {
            final ManagedBinding binding = new WrappedManagedServerSocket(name, socket, this);
            registerBinding(binding);
            return binding;
        }

        /** {@inheritDoc} */
        @Override
        public Closeable registerSocket(String name, DatagramSocket socket) {
            final ManagedBinding binding = new WrappedManagedDatagramSocket(name, socket, this);
            registerBinding(binding);
            return binding;
        }

        /** {@inheritDoc} */
        @Override
        public Closeable registerChannel(String name, SocketChannel channel) {
            final ManagedBinding binding = new NetworkChannelManagedBinding(name, channel, this);
            registerBinding(binding);
            return binding;
        }

        /** {@inheritDoc} */
        @Override
        public Closeable registerChannel(String name, ServerSocketChannel channel) {
            final ManagedBinding binding = new NetworkChannelManagedBinding(name, channel, this);
            registerBinding(binding);
            return binding;
        }

        /** {@inheritDoc} */
        @Override
        public Closeable registerChannel(String name, DatagramChannel channel) {
            final ManagedBinding binding = new NetworkChannelManagedBinding(name, channel, this);
            registerBinding(binding);
            return binding;
        }

        /** {@inheritDoc} */
        @Override
        public void unregisterBinding(String name) {
            if(name == null) {
                return;
            }
            bindings.remove(name);
        }
    }

    private static final class UnnamedRegistryImpl implements UnnamedBindingRegistry {
        // Can't put null in ConcurrentHashMap and I'm too lazy to drop CHM
        private static final Object VALUE = new Object();

        private final Map<WrapperBinding, Object> bindings = new ConcurrentHashMap<>();

        /** {@inheritDoc} */
        @Override
        public void registerBinding(ManagedBinding binding) {
            bindings.put(new WrappedManagedBinding(binding, this), VALUE);
        }

        /** {@inheritDoc} */
        @Override
        public void unregisterBinding(ManagedBinding binding) {
            if (binding != null) {
                final WrapperBinding toRemove = binding instanceof WrapperBinding
                        ? (WrapperBinding) binding
                        : new WrappedManagedBinding(binding, this);
                bindings.remove(toRemove);
            }
        }

        /** {@inheritDoc} */
        @Override
        public Collection<ManagedBinding> listActiveBindings() {
            return new HashSet<ManagedBinding>(bindings.keySet());
        }

        /** {@inheritDoc} */
        @Override
        public Closeable registerSocket(Socket socket) {
            final ManagedBinding binding = new WrappedManagedSocket(socket, this);
            registerBinding(binding);
            return binding;
        }

        /** {@inheritDoc} */
        @Override
        public Closeable registerSocket(ServerSocket socket) {
            final ManagedBinding binding = new WrappedManagedServerSocket(socket, this);
            registerBinding(binding);
            return binding;
        }

        /** {@inheritDoc} */
        @Override
        public Closeable registerSocket(DatagramSocket socket) {
            final ManagedBinding binding = new WrappedManagedDatagramSocket(socket, this);
            registerBinding(binding);
            return binding;
        }

        /** {@inheritDoc} */
        @Override
        public Closeable registerChannel(SocketChannel channel) {
            final ManagedBinding binding = new NetworkChannelManagedBinding(channel, this);
            registerBinding(binding);
            return binding;
        }

        /** {@inheritDoc} */
        @Override
        public Closeable registerChannel(ServerSocketChannel channel) {
            final ManagedBinding binding = new NetworkChannelManagedBinding(channel, this);
            registerBinding(binding);
            return binding;
        }

        /** {@inheritDoc} */
        @Override
        public Closeable registerChannel(DatagramChannel channel) {
            final ManagedBinding binding = new NetworkChannelManagedBinding(channel, this);
            registerBinding(binding);
            return binding;
        }

        /** {@inheritDoc} */
        @Override
        public void unregisterSocket(Socket socket) {
            bindings.remove(new WrappedManagedSocket(socket, this));
        }

        /** {@inheritDoc} */
        @Override
        public void unregisterSocket(ServerSocket socket) {
            bindings.remove(new WrappedManagedServerSocket(socket, this));
        }

        /** {@inheritDoc} */
        @Override
        public void unregisterSocket(DatagramSocket socket) {
            bindings.remove(new WrappedManagedDatagramSocket(socket, this));
        }

        /** {@inheritDoc} */
        @Override
        public void unregisterChannel(SocketChannel channel) {
            WrapperBinding wrapper = new NetworkChannelManagedBinding(channel, this);
            bindings.remove(wrapper);
        }

        /** {@inheritDoc} */
        @Override
        public void unregisterChannel(ServerSocketChannel channel) {
            WrapperBinding wrapper = new NetworkChannelManagedBinding(channel, this);
            bindings.remove(wrapper);
        }

        /** {@inheritDoc} */
        @Override
        public void unregisterChannel(DatagramChannel channel) {
            WrapperBinding wrapper = new NetworkChannelManagedBinding(channel, this);
            bindings.remove(wrapper);
        }
    }

}
