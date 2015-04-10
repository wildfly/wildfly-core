/*
 * Copyright (C) 2015 Red Hat, inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package org.jboss.as.host.controller.discovery;

import io.undertow.util.NetworkUtils;

/**
 * Simple class to hold the connection parameters to a remote domain controller.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2015 Red Hat, inc.
 */
public class RemoteDomainControllerConnectionConfiguration {
    // The host name of the domain controller
    private final String remoteDcHost;

    // The port number of the domain controller
    private final int remoteDcPort;

    // The protocol for the discovery
    private final String remoteDcProtocol;

    /**
     * Creates an initialized connection config instance.
     * In the current implementation, protocol and host can't be null.
     *
     * @param protocol  protocol to use for the discovery
     * @param host  host name of the domain controller
     * @param port  port number of the domain controller
     */
    public RemoteDomainControllerConnectionConfiguration(String protocol, String host, int port) {
        assert protocol != null : "protocol is null";
        assert host != null : "host is null";
        remoteDcHost = NetworkUtils.formatPossibleIpv6Address(host);
        remoteDcPort = port;
        remoteDcProtocol = protocol;
    }

    public String getHost() {
        return remoteDcHost;
    }

    public int getPort() {
        return remoteDcPort;
    }

    public String getProtocol() {
        return remoteDcProtocol;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{protocol=" + remoteDcProtocol + ",host=" + remoteDcHost + ",port=" + remoteDcPort + '}';
    }
}
