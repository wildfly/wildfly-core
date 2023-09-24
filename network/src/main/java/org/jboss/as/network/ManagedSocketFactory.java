/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.SocketFactory;

/**
 * {@code ServerSocketFactory} implementation creating sockets, which automatically register
 * and unregister itself at the {@code SocketBindingManager} when bound or closed.
 *
 * @author Emanuel Muckenhuber
 */
public abstract class ManagedSocketFactory extends SocketFactory {

    /**
     * Create a socket.
     *
     * @param name the socket name
     * @return the socket
     * @throws IOException
     * @see {@linkplain SocketFactory#createSocket()}
     */
    public abstract Socket createSocket(String name) throws IOException;

    /**
     * Create a socket.
     *
     * @param name the socket-binding name.
     * @param host the host
     * @param port the port
     * @return the socket
     * @throws IOException
     * @throws java.net.UnknownHostException
     * @see {@linkplain SocketFactory#createSocket(String, int)}
     */
    public abstract Socket createSocket(String name, String host, int port) throws IOException;

    /**
     * Create a socket.
     *
     * @param name the socket-binding name.
     * @param host the host
     * @param port the port
     * @return the socket
     * @throws IOException
     * @see {@linkplain SocketFactory#createSocket(InetAddress, int)}
     */
    public abstract Socket createSocket(String name, InetAddress host, int port) throws IOException;

    /**
     * Create a socket.
     *
     * @param name the socket-binding name.
     * @param host the host
     * @param port the port
     * @param localHost the local host
     * @param localPort the local port
     * @return the socket
     * @throws IOException
     * @throws java.net.UnknownHostException
     * @see {@linkplain SocketFactory#createSocket(String, int, java.net.InetAddress, int)}
     */
    public abstract Socket createSocket(String name, String host, int port, InetAddress localHost, int localPort) throws IOException;

    /**
     * Create a socket.
     *
     * @param name the socket-binding name.
     * @param address the address
     * @param port the port
     * @param localAddress the local address
     * @param localPort the local port
     * @return the socket
     * @throws IOException
     * @see {@linkplain SocketFactory#createSocket(java.net.InetAddress, int, java.net.InetAddress, int)}
     */
    public abstract Socket createSocket(String name, InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException;

}
