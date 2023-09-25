/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Identifying information for a server in a domain. The unique identity is defined the by
 * {@link #getHostName() host name} and the {@link #getServerName() server name}.
 *
 * @author Brian Stansberry
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ServerIdentity implements Serializable {

    private static final long serialVersionUID = -5853735093238463353L;

    private final String hostName;
    private final String serverName;
    private final String serverGroupName;

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

    @Override
    public int hashCode() {
        int result = 17;
        result += 31 * serverName.hashCode();
        result += 31 * hostName.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (obj instanceof ServerIdentity) {
            ServerIdentity other = (ServerIdentity) obj;
            return Objects.equals(serverName, other.serverName) && Objects.equals(hostName, other.hostName);
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
