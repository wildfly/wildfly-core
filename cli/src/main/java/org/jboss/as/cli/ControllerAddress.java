/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A class used both by the {@link ControllerAddressResolver} and by the configuration to represent the address of a controller.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public final class ControllerAddress {

    private final String protocol;
    private final String host;
    private final int port;

    private final int hashCode;

    public ControllerAddress(final String protocol, final String host, final int port) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;

        hashCode = (protocol == null ? 3 : protocol.hashCode()) * (host == null ? 5 : host.hashCode()) * port;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        try {
            return new URI(protocol, null, host, port, null, null, null).toString();
        } catch (URISyntaxException e) {
            return protocol + "://" + host + ":" + port;
        }
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ControllerAddress ? equals((ControllerAddress) other) : false;
    }

    public boolean equals(ControllerAddress other) {
        return (protocol == null ? other.protocol == null : protocol.equals(other.protocol))
                && (host == null ? other.host == null : host.equals(other.host)) && port == other.port;
    }

}