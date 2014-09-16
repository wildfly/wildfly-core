/*
* JBoss, Home of Professional Open Source.
* Copyright 2013, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/

package org.jboss.as.host.controller.discovery;

import io.undertow.util.NetworkUtils;


import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Handle domain controller discovery via static (i.e., hard-wired) configuration.
 *
 * @author Farah Juma
 */
public class StaticDiscovery implements DiscoveryOption {

    // The host name of the domain controller
    private final String remoteDcHost;

    // The port number of the domain controller
    private final int remoteDcPort;

    // The port number of the domain controller
    private final String remoteDcProtocol;

    public StaticDiscovery(String protocol, String host, int port) {
        remoteDcHost = NetworkUtils.formatPossibleIpv6Address(host);
        remoteDcPort = port;
        remoteDcProtocol = protocol;
    }

    @Override
    public void allowDiscovery(String protocol, String host, int port) {
        // no-op
    }

    @Override
    public void discover() {
        // Validate the host and port
        try {
            StaticDiscoveryResourceDefinition.HOST.getValidator()
                .validateParameter(StaticDiscoveryResourceDefinition.HOST.getName(), new ModelNode(remoteDcHost));
            StaticDiscoveryResourceDefinition.PORT.getValidator()
                .validateParameter(StaticDiscoveryResourceDefinition.PORT.getName(), new ModelNode(remoteDcPort));
            StaticDiscoveryResourceDefinition.PROTOCOL.getValidator()
                .validateParameter(StaticDiscoveryResourceDefinition.PROTOCOL.getName(), new ModelNode(remoteDcProtocol));
        } catch (OperationFailedException e) {
            throw new IllegalStateException(e.getFailureDescription().asString());
        }
    }

    @Override
    public void cleanUp() {
        // no-op
    }

    @Override
    public String getRemoteDomainControllerHost() {
        return remoteDcHost;
    }

    @Override
    public int getRemoteDomainControllerPort() {
        return remoteDcPort;
    }

    @Override
    public String getRemoteDomainControllerProtocol() {
        return remoteDcProtocol;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{protocol=" + remoteDcProtocol + ",host=" + remoteDcHost + ",port=" + remoteDcPort + '}';
    }
}
