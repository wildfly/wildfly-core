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

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * The socket binding manager represents a registry of all
 * active (bound) sockets.
 *
 * @author Emanuel Muckenhuber
 */
public interface SocketBindingManager {

    /**
     * Get the managed server socket factory.
     *
     * @return the server socket factory
     */
    ManagedServerSocketFactory getServerSocketFactory();

    /**
     * Get the socket factory.
     *
     * @return the socket factory
     */
    ManagedSocketFactory getSocketFactory();

    /**
     * Create a named, unbound datagram socket.
     *
     * @param name the name for the managed binding. Cannot be {@code null}
     * @return an unbound datagram socket
     * @throws SocketException
     * @throws IllegalArgumentException if {@code name} is {@code null}
     */
    DatagramSocket createDatagramSocket(final String name) throws SocketException;

    /**
     * Create an unnamed, unbound datagram socket.
     *
     * @return an unbound datagram socket
     * @throws SocketException
     */
    DatagramSocket createDatagramSocket() throws SocketException;

    /**
     * Create a named datagram socket.
     *
     * @param name the name for the managed binding. Cannot be {@code null}
     * @param address the socket address. Cannot be {@code null}
     * @return the datagram socket
     * @throws SocketException
     * @throws IllegalArgumentException if {@code name} or {@code address} is {@code null}
     */
    DatagramSocket createDatagramSocket(final String name, final SocketAddress address) throws SocketException;

    /**
     * Create an unnamed datagram socket.
     *
     * @param address the socket address. Cannot be {@code null}
     * @return the datagram socket
     * @throws SocketException
     * @throws IllegalArgumentException if {@code address} is {@code null}
     */
    DatagramSocket createDatagramSocket(final SocketAddress address) throws SocketException;

    /**
     * Create a named, unbound multicast socket.
     *
     * @param name the name for the managed binding. Cannot be {@code null}
     * @return an unbound multicast socket
     * @throws IOException
     * @throws IllegalArgumentException if {@code name} is {@code null}
     */
    MulticastSocket createMulticastSocket(final String name) throws IOException;

    /**
     * Create an unnamed, unbound multicast socket.
     *
     * @return an unbound multicast socket
     * @throws IOException
     */
    MulticastSocket createMulticastSocket() throws IOException;

    /**
     * Create a named multicast socket.
     *
     * @param name the name for the managed binding. Cannot be {@code null}
     * @param address the socket address. Cannot be {@code null}
     * @return the multicast socket
     * @throws IOException
     * @throws IllegalArgumentException if {@code name} or {@code address} is {@code null}
     */
    MulticastSocket createMulticastSocket(final String name, final SocketAddress address) throws IOException;

    /**
     * Create an unnamed multicast socket.
     *
     * @param address the socket address. Cannot be {@code null}
     * @return the multicast socket
     * @throws IOException
     * @throws IllegalArgumentException if {@code address} is {@code null}
     */
    MulticastSocket createMulticastSocket(final SocketAddress address) throws IOException;

    /**
     * Return the resolved {@link InetAddress} for the default interface.
     *
     * @return the resolve address
     */
    InetAddress getDefaultInterfaceAddress();

    /**
     * Return the {@link NetworkInterfaceBinding} for the default interface.
     *
     * @return the network interface binding
     */
    NetworkInterfaceBinding getDefaultInterfaceBinding();

    /**
     * Get the server port offset.
     * TODO move to somewhere else...
     *
     * @return the port offset
     */
    int getPortOffset();

    /**
     * Get the named binding registry.
     *
     * @return the named registry
     */
    NamedManagedBindingRegistry getNamedRegistry();

    /**
     * Get the registry for unnamed open sockets.
     *
     * @return the unnamed registry
     */
    UnnamedBindingRegistry getUnnamedRegistry();

    interface NamedManagedBindingRegistry extends ManagedBindingRegistry {

        /**
         * Gets the binding registered under the given name.
         * @param name the name
         * @return the binding, or {@code null} if there is no binding registered with that name
         */
        ManagedBinding getManagedBinding(final String name);

        /**
         * Gets whether there is a binding registered under the given name.
         * @param name the name
         * @return {@code true} if there is a binding under that name
         */
        boolean isRegistered(final String name);

        /**
         * Registers a binding under the given name based on the given socket.
         * @param name the name. Cannot be {@code null}
         * @param socket the socket. Cannot be {@code null}
         * @return a {@link Closeable} that will unregister the binding if {@code close()} is called
         */
        Closeable registerSocket(String name, Socket socket);

        /**
         * Registers a binding under the given name based on the given socket.
         * @param name the name. Cannot be {@code null}
         * @param socket the socket. Cannot be {@code null}
         * @return a {@link Closeable} that will unregister the binding if {@code close()} is called
         */
        Closeable registerSocket(String name, ServerSocket socket);

        /**
         * Registers a binding under the given name based on the given socket.
         * @param name the name. Cannot be {@code null}
         * @param socket the socket. Cannot be {@code null}
         * @return a {@link Closeable} that will unregister the binding if {@code close()} is called
         */
        Closeable registerSocket(String name, DatagramSocket socket);

        /**
         * Registers a binding under the given name based on the given channel.
         * @param name the name. Cannot be {@code null}
         * @param channel the channel. Cannot be {@code null}
         * @return a {@link Closeable} that will unregister the binding if {@code close()} is called
         */
        Closeable registerChannel(String name, SocketChannel channel);

        /**
         * Registers a binding under the given name based on the given channel.
         * @param name the name. Cannot be {@code null}
         * @param channel the channel. Cannot be {@code null}
         * @return a {@link Closeable} that will unregister the binding if {@code close()} is called
         */
        Closeable registerChannel(String name, ServerSocketChannel channel);

        /**
         * Registers a binding under the given name based on the given channel.
         * @param name the name. Cannot be {@code null}
         * @param channel the channel. Cannot be {@code null}
         * @return a {@link Closeable} that will unregister the binding if {@code close()} is called
         */
        Closeable registerChannel(String name, DatagramChannel channel);

        /**
         * Unregisters the binding with the given name.
         *
         * @param name the name
         */
        void unregisterBinding(String name);

        /**
         * {@inheritDoc}
         *
         * @throws IllegalStateException if {@link ManagedBinding#getSocketBindingName()} returns {@code null}
         */
        @Override
        void registerBinding(final ManagedBinding binding);

        /**
         * {@inheritDoc}
         *
         * @throws IllegalStateException if {@link ManagedBinding#getSocketBindingName()} returns {@code null}
         */
        @Override
        void unregisterBinding(final ManagedBinding binding);

    }

    interface UnnamedBindingRegistry extends ManagedBindingRegistry {

        /**
         * Registers an unnamed binding based on the given socket.
         * @param socket the socket. Cannot be {@code null}
         * @return a {@link Closeable} that will unregister the binding and close the socket
         *         if {@code close()} is called
         */
        Closeable registerSocket(Socket socket);

        /**
         * Registers an unnamed binding based on the given socket.
         * @param socket the socket. Cannot be {@code null}
         * @return a {@link Closeable} that will unregister the binding and close the socket
         *         if {@code close()} is called
         */
        Closeable registerSocket(ServerSocket socket);

        /**
         * Registers an unnamed binding based on the given socket.
         * @param socket the socket. Cannot be {@code null}
         * @return a {@link Closeable} that will unregister the binding and close the socket
         *         if {@code close()} is called
         */
        Closeable registerSocket(DatagramSocket socket);

        /**
         * Registers an unnamed binding based on the given channel.
         * @param channel the channel. Cannot be {@code null}
         * @return a {@link Closeable} that will unregister the binding and close the socket
         *         if {@code close()} is called
         */
        Closeable registerChannel(SocketChannel channel);

        /**
         * Registers an unnamed binding based on the given channel.
         * @param channel the channel. Cannot be {@code null}
         * @return a {@link Closeable} that will unregister the binding and close the socket
         *         if {@code close()} is called
         */
        Closeable registerChannel(ServerSocketChannel channel);

        /**
         * Registers an unnamed binding based on the given channel.
         * @param channel the channel. Cannot be {@code null}
         * @return a {@link Closeable} that will unregister the binding and close the socket
         *         if {@code close()} is called
         */
        Closeable registerChannel(DatagramChannel channel);

        /**
         * Unregisters a binding previously {@link #registerSocket(Socket) registered for the socket}.
         *
         * @param socket the socket previously passed to {@link #registerSocket(Socket)}. Cannot be {@code null}
         */
        void unregisterSocket(Socket socket);

        /**
         * Unregisters a binding previously {@link #registerSocket(ServerSocket) registered for the socket}.
         *
         * @param socket the socket previously passed to {@link #registerSocket(ServerSocket)}. Cannot be {@code null}
         */
        void unregisterSocket(ServerSocket socket);

        /**
         * Unregisters a binding previously {@link #registerSocket(DatagramSocket) registered for the socket}.
         *
         * @param socket the socket previously passed to {@link #registerSocket(DatagramSocket)}. Cannot be {@code null}
         */
        void unregisterSocket(DatagramSocket socket);

        /**
         * Unregisters a binding previously {@link #registerChannel(SocketChannel) registered for the channel}.
         *
         * @param channel the channel previously passed to {@link #registerChannel(SocketChannel)}. Cannot be {@code null}
         */
        void unregisterChannel(SocketChannel channel);

        /**
         * Unregisters a binding previously {@link #registerChannel(ServerSocketChannel) registered for the channel}.
         *
         * @param channel the channel previously passed to {@link #registerChannel(ServerSocketChannel)}. Cannot be {@code null}
         */
        void unregisterChannel(ServerSocketChannel channel);

        /**
         * Unregisters a binding previously {@link #registerChannel(DatagramChannel) registered for the channel}.
         *
         * @param channel the channel previously passed to {@link #registerChannel(DatagramChannel)}. Cannot be {@code null}
         */
        void unregisterChannel(DatagramChannel channel);

    }

}

