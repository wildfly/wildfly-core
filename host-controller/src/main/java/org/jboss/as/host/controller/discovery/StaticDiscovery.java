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


import java.util.Collections;
import java.util.List;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Handle domain controller discovery via static (i.e., hard-wired) configuration.
 *
 * @author Farah Juma
 */
public class StaticDiscovery implements DiscoveryOption {

    // The host name of the domain controller
    private final RemoteDomainControllerConnectionConfiguration parameters;

    /**
     * Creates an initialized static discovery config instance.
     * In the current implementation, protocol and host can't be null.
     *
     * @param protocol  protocol to use for the discovery
     * @param host  host name of the domain controller
     * @param port  port number of the domain controller
     */
    public StaticDiscovery(String protocol, String host, int port) {
        this.parameters = new RemoteDomainControllerConnectionConfiguration(protocol, host, port);
    }

    @Override
    public void allowDiscovery(List<DomainControllerManagementInterface> interfaces) {
        // no-op
    }

    @Override
    public List<RemoteDomainControllerConnectionConfiguration> discover() {
        // Validate the host and port
        try {
            StaticDiscoveryResourceDefinition.HOST.getValidator()
                .validateParameter(StaticDiscoveryResourceDefinition.HOST.getName(), new ModelNode(parameters.getHost()));
            StaticDiscoveryResourceDefinition.PORT.getValidator()
                .validateParameter(StaticDiscoveryResourceDefinition.PORT.getName(), new ModelNode(parameters.getPort()));
            StaticDiscoveryResourceDefinition.PROTOCOL.getValidator()
                .validateParameter(StaticDiscoveryResourceDefinition.PROTOCOL.getName(), new ModelNode(parameters.getProtocol()));
        } catch (OperationFailedException e) {
            throw new IllegalStateException(e.getFailureDescription().asString());
        }
        return Collections.singletonList(this.parameters);
    }

    @Override
    public void cleanUp() {
        // no-op
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{protocol=" + parameters.getProtocol() + ",host=" + parameters.getHost() + ",port=" + parameters.getPort() + '}';
    }
}
