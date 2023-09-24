/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

import javax.net.ServerSocketFactory;

/**
 * {@code ServerSocketFactory} implementation creating sockets, which automatically register
 * and unregister itself at the {@code SocketBindingManager} when bound or closed.
 *
 * @author Emanuel Muckenhuber
 */
public abstract class ManagedServerSocketFactory extends ServerSocketFactory {

    /**
     * Create a named server socket.
     *
     * @param name the socket-binding name
     * @return the server socket
     * @throws IOException
     */
    public abstract ServerSocket createServerSocket(String name) throws IOException;

    /**
     * Create named server socket.
     *
     * @param name the socket-binding name
     * @param port the port
     * @return the server socket
     * @throws IOException
     * @see {@linkplain ServerSocketFactory#createServerSocket(int)}
     */
    public abstract ServerSocket createServerSocket(String name, int port) throws IOException;

    /**
     * Create a named server socket.
     *
     * @param name the socket-binding name
     * @param port the port
     * @param backlog the backlog
     * @return the server socket
     * @throws IOException
     * @see {@linkplain ServerSocketFactory#createServerSocket(int, int)}
     */
    public abstract ServerSocket createServerSocket(String name, int port, int backlog) throws IOException;

    /**
     * Create a named server socket.
     *
     * @param name the socket-binding name
     * @param port the port
     * @param backlog the backlog
     * @param ifAddress the interface address
     * @return the server socket
     * @throws IOException
     * @see {@linkplain ServerSocketFactory#createServerSocket(int, int)}
     */
    public abstract ServerSocket createServerSocket(String name, int port, int backlog, InetAddress ifAddress) throws IOException;

}
