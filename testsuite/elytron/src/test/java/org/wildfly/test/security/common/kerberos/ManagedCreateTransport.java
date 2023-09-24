/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.security.common.kerberos;

import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.annotations.TransportType;

/**
 * A helper implementation of {@link CreateTransport} annotation which allows to configure values.
 *
 * @author Josef Cacek
 */
public class ManagedCreateTransport extends AnnotationLiteral<CreateTransport> implements CreateTransport {

    private static final long serialVersionUID = 1L;

    /** The name for this protocol */
    private String protocol;
    /** The transport type (TCP or UDP) Default to TCP */
    private TransportType type;
    /** The port to use, default to a bad value so that we know we have to pick one random available port */
    private int port;
    /** The InetAddress for this transport. Default to localhost */
    private String address;
    /** The backlog. Default to 50 */
    private int backlog;
    /** A flag to tell if the transport is SSL based. Default to false */
    private boolean ssl;
    /** The number of threads to use. Default to 3 */
    private int nbThreads;

    // Constructors ----------------------------------------------------------

    /**
     * Create a new ManagedCreateTransport.
     *
     * @param createLdapServer
     */
    public ManagedCreateTransport(final CreateTransport original) {
        protocol = original.protocol();
        type = original.type();
        port = original.port();
        address = original.address();
        backlog = original.backlog();
        ssl = original.ssl();
        nbThreads = original.nbThreads();
    }

    // Public methods --------------------------------------------------------

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateTransport#protocol()
     */
    public String protocol() {
        return protocol;
    }

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateTransport#type()
     */
    public TransportType type() {
        return type;
    }

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateTransport#port()
     */
    public int port() {
        return port;
    }

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateTransport#address()
     */
    public String address() {
        return address;
    }

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateTransport#backlog()
     */
    public int backlog() {
        return backlog;
    }

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateTransport#ssl()
     */
    public boolean ssl() {
        return ssl;
    }

    /**
     *
     * @return
     * @see org.apache.directory.server.annotations.CreateTransport#nbThreads()
     */
    public int nbThreads() {
        return nbThreads;
    }

    /**
     * Set the protocol.
     *
     * @param protocol The protocol to set.
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * Set the type.
     *
     * @param type The type to set.
     */
    public void setType(TransportType type) {
        this.type = type;
    }

    /**
     * Set the port.
     *
     * @param port The port to set.
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Set the address.
     *
     * @param address The address to set.
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * Set the backlog.
     *
     * @param backlog The backlog to set.
     */
    public void setBacklog(int backlog) {
        this.backlog = backlog;
    }

    /**
     * Set the ssl.
     *
     * @param ssl The ssl to set.
     */
    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    /**
     * Set the nbThreads.
     *
     * @param nbThreads The nbThreads to set.
     */
    public void setNbThreads(int nbThreads) {
        this.nbThreads = nbThreads;
    }
}
