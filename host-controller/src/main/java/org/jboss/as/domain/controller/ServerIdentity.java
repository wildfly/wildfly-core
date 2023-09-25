/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller;

import java.io.Serializable;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;

/**
 * Identifying information for a server in a domain. A bit of a misnomer, as
 * the server's name is sufficient identification since all servers in a
 * domain must have unique names.
 *
 * @author Brian Stansberry
 */
public class ServerIdentity implements Serializable {

    private static final long serialVersionUID = -5853735093238463353L;

    private final String hostName;
    private final String serverName;
    private final String serverGroupName;
    private volatile PathAddress pathAddress;

    public ServerIdentity(final String hostName, final String serverGroupName, final String serverName) {
        this.hostName = hostName;
        this.serverGroupName = serverGroupName;
        this.serverName = serverName;
    }

    public String getServerGroupName() {
        return serverGroupName;
    }

    public String getHostName() {
        return hostName;
    }

    public String getServerName() {
        return serverName;
    }

    public PathAddress toPathAddress() {
        if (pathAddress == null) {
            pathAddress = PathAddress.pathAddress(
                    PathElement.pathElement(ModelDescriptionConstants.HOST, hostName),
                    PathElement.pathElement(ModelDescriptionConstants.RUNNING_SERVER, serverName)
            );
        }
        return pathAddress;
    }

    /**
     * Returns the hash code of the server name and host name.
     */
    @Override
    public int hashCode() {
        int result = 17;
        result += 31 * serverName.hashCode();
        result += 31 * hostName.hashCode();
        return result;
    }

    /**
     * Returns {@code true} if {@code obj} is a HostedServer with the same server name and host name.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (obj instanceof ServerIdentity) {
            ServerIdentity other = (ServerIdentity) obj;
            return serverName.equals(other.serverName) && hostName.equals(other.hostName);
        }
        return false;
    }

    @Override
    public String toString() {
        return new StringBuilder(getClass().getSimpleName())
            .append("{name=")
            .append(serverName)
            .append(", host=")
            .append(hostName)
            .append(", server-group=")
            .append(serverGroupName)
            .append("}")
            .toString();
    }
}
