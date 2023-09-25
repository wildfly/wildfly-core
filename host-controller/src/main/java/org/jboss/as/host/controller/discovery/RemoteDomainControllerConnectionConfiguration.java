/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
