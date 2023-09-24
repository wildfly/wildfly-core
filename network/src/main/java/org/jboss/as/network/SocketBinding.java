/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.network;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;

import static org.jboss.as.network.logging.NetworkMessages.MESSAGES;

/**
 * An encapsulation of socket binding related information.
 *
 * @author Emanuel Muckenhuber
 */
public final class SocketBinding {

    private final String name;
    private volatile int port;
    private volatile boolean isFixedPort;
    private volatile InetAddress multicastAddress;
    private volatile int multicastPort;
    private volatile List<ClientMapping> clientMappings;
    private final NetworkInterfaceBinding networkInterface;
    private final SocketBindingManager socketBindings;

    public SocketBinding(final String name, int port, boolean isFixedPort, InetAddress multicastAddress, int multicastPort,
                         final NetworkInterfaceBinding networkInterface, SocketBindingManager socketBindings, List<ClientMapping> clientMappings) {
        this.name = name;
        this.port = port;
        this.isFixedPort = isFixedPort;
        this.multicastAddress = multicastAddress;
        this.multicastPort = multicastPort;
        this.socketBindings = socketBindings;
        this.networkInterface = networkInterface;
        this.clientMappings = clientMappings == null ? Collections.<ClientMapping>emptyList() : fixupMappings(clientMappings);
    }

    private List<ClientMapping> fixupMappings(List<ClientMapping> clientMappings) {
        for (ClientMapping mapping : clientMappings) {
            mapping.updatePortIfUnknown(calculatePort());
        }

        return clientMappings;
    }

    /**
     * Return the name of the SocketBinding used in the configuration
     *
     * @return the SocketBinding configuration name
     */
    public String getName() {
        return name;
    }

    /**
     * Return the resolved {@link InetAddress} for this binding.
     *
     * @return the resolve address
     */
    public InetAddress getAddress() {
        return networkInterface != null ? networkInterface.getAddress() : socketBindings.getDefaultInterfaceAddress();
    }

    /**
     * Return the {@link NetworkInterfaceBinding} for the default interface.
     *
     * @return the network interface binding
     */
    public NetworkInterfaceBinding getNetworkInterfaceBinding() {
        return networkInterface != null ? networkInterface : socketBindings.getDefaultInterfaceBinding();
    }

    /**
     * Get the socket binding manager.
     *
     * @return the socket binding manger
     */
    public SocketBindingManager getSocketBindings() {
        return socketBindings;
    }

    private int calculatePort() {
        int port = this.port;
        // 0 is a reserved port number indicating usage of system-allocated dynamic ports
        // and thus port offset must not be applied
        if (!isFixedPort && port != 0) {
            port += socketBindings.getPortOffset();
        }
        return port;
    }

    /**
     * Get the socket address.
     *
     * @return the socket address
     */
    public InetSocketAddress getSocketAddress() {
        int port = calculatePort();
        return new InetSocketAddress(getAddress(), port);
    }

    /**
     * Get the multicast socket address.
     *
     * @return the multicast address
     */
    public InetSocketAddress getMulticastSocketAddress() {
        if (multicastAddress == null) {
            throw MESSAGES.noMulticastBinding(name);
        }
        return new InetSocketAddress(multicastAddress, multicastPort);
    }

    /**
     * Create and bind a server socket
     *
     * @return the server socket
     * @throws IOException
     */
    public ServerSocket createServerSocket() throws IOException {
        final ServerSocket socket = getServerSocketFactory().createServerSocket(name);
        socket.bind(getSocketAddress());
        return socket;
    }

    /**
     * Create and bind a server socket.
     *
     * @param backlog the backlog
     * @return the server socket
     * @throws IOException
     */
    public ServerSocket createServerSocket(int backlog) throws IOException {
        final ServerSocket socket = getServerSocketFactory().createServerSocket(name);
        socket.bind(getSocketAddress(), backlog);
        return socket;
    }

    /**
     * Create and bind a datagram socket.
     *
     * @return the datagram socket
     * @throws SocketException
     */
    public DatagramSocket createDatagramSocket() throws SocketException {
        return socketBindings.createDatagramSocket(name, getMulticastSocketAddress());
    }

    /**
     * Create a multicast socket.
     *
     * @return the multicast socket
     * @throws IOException
     */
    public MulticastSocket createMulticastSocket() throws IOException {
        return socketBindings.createMulticastSocket(name, getMulticastSocketAddress());
    }

    /**
     * Get the {@code ManagedBinding} associated with this {@code SocketBinding}.
     *
     * @return the managed binding if bound, <code>null</code> otherwise
     */
    public ManagedBinding getManagedBinding() {
        final SocketBindingManager.NamedManagedBindingRegistry registry = this.socketBindings.getNamedRegistry();
        return registry.getManagedBinding(name);
    }

    /**
     * Check whether this {@code SocketBinding} is bound. All bound sockets
     * have to be registered at the {@code SocketBindingManager} against which
     * this check is performed.
     *
     * @return true if bound, false otherwise
     */
    public boolean isBound() {
        final SocketBindingManager.NamedManagedBindingRegistry registry = this.socketBindings.getNamedRegistry();
        return registry.isRegistered(name);
    }

    /**
     * Returns the port configured for this socket binding.
     * <p/>
     * Note that this method does NOT take into account any port-offset that might have been configured. Use {@link #getAbsolutePort()}
     * if the port-offset has to be considered.
     *
     * @return port number configured for this socket binding without taking into account any port-offset
     */
    public int getPort() {
        return port;
    }

    //TODO restrict access
    public void setPort(int port) {
        checkNotBound();
        this.port = port;
    }

    public boolean isFixedPort() {
        return isFixedPort;
    }

    //TODO restrict access
    public void setFixedPort(boolean fixedPort) {
        checkNotBound();
        isFixedPort = fixedPort;
    }

    public int getMulticastPort() {
        return multicastPort;
    }

    //TODO restrict access
    public void setMulticastPort(int multicastPort) {
        checkNotBound();
        this.multicastPort = multicastPort;
    }

    public InetAddress getMulticastAddress() {
        return multicastAddress;
    }

    //TODO restrict access
    public void setMulticastAddress(InetAddress multicastAddress) {
        checkNotBound();
        this.multicastAddress = multicastAddress;
    }

    public void setClientMappings(List<ClientMapping> clientMappings) {
        this.clientMappings = clientMappings;
    }

    public List<ClientMapping> getClientMappings() {
        return clientMappings;
    }

    /**
     * Unlike the {@link #getPort()} method, this method takes into account the port offset, if the port is <i>not</i> a fixed
     * port nor dynamically allocated ephemeral port (port number 0) and returns the absolute port number which is the sum of
     * the port offset and the (relative) port.
     *
     * @return port number configured for this socket binding taking port-offset into account when appropriate
     */
    public int getAbsolutePort() {
        return calculatePort();
    }

    void checkNotBound() {
        if(isBound()) {
            throw MESSAGES.cannotChangeWhileBound();
        }
    }

    ManagedSocketFactory getSocketFactory() {
        return socketBindings.getSocketFactory();
    }

    ManagedServerSocketFactory getServerSocketFactory() {
        return socketBindings.getServerSocketFactory();
    }

}
