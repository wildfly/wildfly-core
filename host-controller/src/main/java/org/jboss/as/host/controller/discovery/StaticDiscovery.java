/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
