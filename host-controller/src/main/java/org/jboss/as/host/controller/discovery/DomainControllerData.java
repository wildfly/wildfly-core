/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.discovery;

import java.io.DataInput;
import java.io.DataOutput;

/**
 * Encapsulates information about a domain controller (i.e., host and port).
 *
 * @author Farah Juma
 */
public class DomainControllerData {

    protected String host;
    protected int port;
    protected String protocol;

    public DomainControllerData() {
    }

    /**
     * Create the DomainControllerData.
     *
     * @param protocol the protocol used by the domain controller
     * @param host the host name of the domain controller
     * @param port the port number of the domain controller
     */
    public DomainControllerData(String protocol, String host, int port) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
    }

    /**
     *  Gets the domain controller's host name.
     *
     *  @return the host name
     */
    public String getHost() {
        return host;
    }

    /**
     *  Gets the domain controller's port number.
     *
     *  @return the port number
     */
    public int getPort() {
        return port;
    }

    /**
     *  Gets the domain controller's protocol.
     *
     *  @return the protocol
     */
    public String getProtocol() {
        return protocol;
    }
    /**
     * Write the domain controller's data to an output stream.
     *
     * @param outstream the output stream
     * @throws Exception
     */
    public void writeTo(DataOutput outstream) throws Exception {
        S3Util.writeString(host, outstream);
        outstream.writeInt(port);
        S3Util.writeString(protocol, outstream);
    }

    /**
     * Read the domain controller's data from an input stream.
     *
     * @param instream the input stream
     * @throws Exception
     */
    public void readFrom(DataInput instream) throws Exception {
        host = S3Util.readString(instream);
        port = instream.readInt();
        protocol = S3Util.readString(instream);
    }

    @Override
    public String toString() {
        StringBuilder sb=new StringBuilder();
        sb.append("primary_host=").append(getHost());
        sb.append(",primary_port=").append(getPort());
        sb.append(",primary_protocol=").append(getProtocol());
        return sb.toString();
    }
}
