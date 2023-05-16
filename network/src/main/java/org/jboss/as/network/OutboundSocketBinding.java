/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.network;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import org.wildfly.common.Assert;
import org.wildfly.service.descriptor.UnaryServiceDescriptor;

/**
 * An outbound socket binding represents the client end of a socket. It represents binding from a local "host"
 * to a remote "host". In some special cases the remote host can itself be the same local host.
 * Unlike the {@link SocketBinding} which represents a {@link java.net.ServerSocket} that opens a socket for "listening",
 * the {@link OutboundSocketBinding} represents a {@link Socket} which "connects" to a remote/local host.
 *
 * @author Jaikiran Pai
 */
public class OutboundSocketBinding {
    public static final UnaryServiceDescriptor<OutboundSocketBinding> SERVICE_DESCRIPTOR = UnaryServiceDescriptor.of("org.wildfly.network.outbound-socket-binding", OutboundSocketBinding.class);

    private final String name;
    private final SocketBindingManager socketBindingManager;
    private final boolean fixedSourcePort;
    private final NetworkInterfaceBinding sourceNetworkInterface;
    private final Integer sourcePort;
    private final String unresolvedDestinationAddress;
    private final int destinationPort;

    /**
     * The destination address is lazily resolved whenever a request is made {@link #getResolvedDestinationAddress()}
     * or for {@link #connect()}.
     */
    private InetAddress resolvedDestinationAddress;

    /**
     * Creates an outbound socket binding
     *
     * @param name                   Name of the outbound socket binding
     * @param socketBindingManager   The socket binding manager
     * @param destinationAddress     The destination address to which this socket will be "connected". Cannot be null or empty string.
     * @param destinationPort        The destination port. Cannot be < 0.
     * @param sourceNetworkInterface (Optional) source network interface which will be used as the "source" of the socket binding
     * @param sourcePort             (Optional) source port. Cannot be null or < 0
     * @param fixedSourcePort        True if the <code>sourcePort</code> has to be used as a fixed port number. False if the <code>sourcePort</code>
     *                               will be added to the port offset while determining the absolute source port.
     */
    public OutboundSocketBinding(final String name, final SocketBindingManager socketBindingManager,
                                 final String destinationAddress, final int destinationPort,
                                 final NetworkInterfaceBinding sourceNetworkInterface, final Integer sourcePort,
                                 final boolean fixedSourcePort) {
        Assert.checkNotNullParam("name", name);
        Assert.checkNotEmptyParam("name", name);
        Assert.checkNotNullParam("socketBindingManager", socketBindingManager);
        Assert.checkNotNullParam("destinationAddress", destinationAddress);
        Assert.checkMinimumParameter("destinationPort", 0, destinationPort);
        this.name = name;
        this.socketBindingManager = socketBindingManager;
        this.unresolvedDestinationAddress = destinationAddress;
        this.destinationPort = destinationPort;
        this.sourceNetworkInterface = sourceNetworkInterface;
        this.sourcePort = sourcePort;
        this.fixedSourcePort = fixedSourcePort;
    }

    /**
     * Creates an outbound socket binding.
     *
     * @param name                   Name of the outbound socket binding
     * @param socketBindingManager   The socket binding manager
     * @param destinationAddress     The destination address to which this socket will be "connected". Cannot be null.
     * @param destinationPort        The destination port. Cannot be < 0.
     * @param sourceNetworkInterface (Optional) source network interface which will be used as the "source" of the socket binding
     * @param sourcePort             (Optional) source port. Cannot be null or < 0
     * @param fixedSourcePort        True if the <code>sourcePort</code> has to be used as a fixed port number. False if the <code>sourcePort</code>
     *                               will be added to the port offset while determining the absolute source port.
     */
    public OutboundSocketBinding(final String name, final SocketBindingManager socketBindingManager,
                                 final InetAddress destinationAddress, final int destinationPort,
                                 final NetworkInterfaceBinding sourceNetworkInterface, final Integer sourcePort,
                                 final boolean fixedSourcePort) {
        this(name, socketBindingManager, destinationAddress.getHostAddress(), destinationPort, sourceNetworkInterface, sourcePort, fixedSourcePort);
        this.resolvedDestinationAddress = destinationAddress;
    }

    /**
     * Creates a {@link Socket} represented by this {@link OutboundSocketBinding} and connects to the
     * destination.
     *
     * @return the created and connected socket
     * @throws IOException
     */
    public Socket connect() throws IOException {
        final Socket socket = this.createSocket();
        final InetAddress destinationAddress = this.getResolvedDestinationAddress();
        final int destinationPort = this.getDestinationPort();
        final SocketAddress destination = new InetSocketAddress(destinationAddress, destinationPort);
        socket.connect(destination);

        return socket;
    }

    /**
     * Returns the name of this outbound socket binding. Can be used in log statements to make the log statement usable.
     *
     * @return the name of this outbound socket binding
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the <em>unresolved</em> destination address of this outbound socket binding.
     *
     * @return the unresolved destination address
     */
    public String getUnresolvedDestinationAddress() {
        return this.unresolvedDestinationAddress;
    }

    /**
     * Returns the <em>resolved</em> destination address of this outbound socket binding. If the destination address
     * is already resolved then this method return that address or else it tries to resolve the
     * address before return.
     *
     * @throws UnknownHostException If the destination address cannot be resolved
     */
    public synchronized InetAddress getResolvedDestinationAddress() throws UnknownHostException {
        if (this.resolvedDestinationAddress != null) {
            return this.resolvedDestinationAddress;
        }
        return InetAddress.getByName(this.unresolvedDestinationAddress);
    }

    /**
     * Returns the destination port number.
     *
     * @return destination port number
     */
    public int getDestinationPort() {
        return this.destinationPort;
    }

    /**
     * Returns whether the source port is fixed, i.e. not accounting for port offset.
     *
     * @return true if the port number is fixed, false otherwise
     */
    public boolean isFixedSourcePort() {
        return this.fixedSourcePort;
    }

    /**
     * Returns the source address of this outbound socket binding. If no explicit source address is specified
     * for this binding, then this method returns the address of the default interface that's configured
     * for the socket binding group.
     *
     * @return source address of this outbound socket binding if specified,
     *         default interface of the socket binding manager otherwise
     */
    public InetAddress getSourceAddress() {
        return this.sourceNetworkInterface != null ? this.sourceNetworkInterface.getAddress() : this.socketBindingManager.getDefaultInterfaceAddress();
    }

    /**
     * Returns the source address of this outbound socket binding if one is configured. If no explicit source address
     * is specified for this binding, then this method returns {@code null}. Use {@link #getSourceAddress()}
     * instead to obtain the default interface of the socket binding manager if none is specified for this binding.
     *
     * @return source address of this outbound socket binding if specified,
     *         {@code null} otherwise
     */
    public InetAddress getOptionalSourceAddress() {
        return sourceNetworkInterface != null ? sourceNetworkInterface.getAddress() : null;
    }

    /**
     * Returns the source port for this outbound socket binding. Note that this isn't the "absolute" port if the
     * this outbound socket binding has a port offset. To get the absolute source port, use the {@link #getAbsoluteSourcePort()}
     * method.
     *
     * @return the source port for this outbound socket binding not accounting for port offset/fixation; {@code null} if an ephemeral port should be used
     */
    public Integer getSourcePort() {
        return (this.sourcePort == null || this.sourcePort == 0) ? null : this.sourcePort;
    }

    /**
     * Returns the absolute source port for this outbound socket binding. The absolute source port is the same as {@link #getSourcePort()}
     * if the outbound socket binding is marked for "fixed source port". Else, it is the sum of {@link #getSourcePort()}
     * and the port offset configured on the {@link SocketBindingManager}.
     *
     * @return the absolute source port accounting for port offset/fixation; {@code null} if an ephemeral port should be used
     */
    public Integer getAbsoluteSourcePort() {
        if (this.getSourcePort() == null) {
            return null;
        }
        if (this.fixedSourcePort) {
            return this.sourcePort;
        }
        final int portOffset = this.socketBindingManager.getPortOffset();
        return this.sourcePort + portOffset;
    }

    /**
     * Closes the outbound socket binding connection.
     *
     * @throws IOException
     */
    public void close() throws IOException {
        final ManagedBinding binding = this.socketBindingManager.getNamedRegistry().getManagedBinding(this.name);
        if (binding == null) {
            return;
        }
        binding.close();
    }

    /**
     * Returns true if a socket connection has been established by this outbound socket binding, false otherwise.
     *
     * @return true if a socket connection has been established by this outbound socket binding, false otherwise
     */
    public boolean isConnected() {
        return this.socketBindingManager.getNamedRegistry().getManagedBinding(this.name) != null;
    }

    // At this point, don't really expose this createSocket() method and let's just expose
    // the connect() method, since the caller can actually misuse the returned Socket
    // to connect any random destination address/port.
    private Socket createSocket() throws IOException {
        final ManagedSocketFactory socketFactory = this.socketBindingManager.getSocketFactory();
        final Socket socket = socketFactory.createSocket(this.name);
        // if the outbound binding specifies the source to use, then bind this socket to the
        // appropriate source
        final SocketAddress sourceSocketAddress = this.getOptionalSourceSocketAddress();
        if (sourceSocketAddress != null) {
            socket.bind(sourceSocketAddress);
        }
        return socket;
    }

    private SocketAddress getOptionalSourceSocketAddress() {
        final InetAddress sourceAddress = this.getOptionalSourceAddress();
        final Integer absoluteSourcePort = this.getAbsoluteSourcePort();
        if (sourceAddress == null && absoluteSourcePort == null) {
            return null;
        }
        if (sourceAddress == null) {
            return new InetSocketAddress(absoluteSourcePort);
        }
        return new InetSocketAddress(sourceAddress, absoluteSourcePort);
    }

}
