/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.network.OutboundConnection;

/**
 * @author Jaikiran Pai
 */
abstract class AbstractOutboundConnectionResourceDefinition extends SimpleResourceDefinition {

    static final String OUTBOUND_CONNECTION_CAPABILITY_NAME = "org.wildfly.remoting.outbound-connection";
    static final String OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME = "org.wildfly.network.outbound-socket-binding";
    static final RuntimeCapability<Void> OUTBOUND_CONNECTION_CAPABILITY =
            RuntimeCapability.Builder.of(OUTBOUND_CONNECTION_CAPABILITY_NAME, true, OutboundConnection.class)
                    .addRequirements(Capabilities.REMOTING_ENDPOINT_CAPABILITY_NAME)
                    .build();

    protected AbstractOutboundConnectionResourceDefinition(final Parameters parameters) {
        super(parameters.addCapabilities(OUTBOUND_CONNECTION_CAPABILITY));
    }

    public abstract void registerChildren(ManagementResourceRegistration resourceRegistration);

    /**
     * Returns the write attribute handler for the <code>attribute</code>
     * @param attribute The attribute for which the write operation handler is being queried
     * @return the handler
     */
    protected abstract OperationStepHandler getWriteAttributeHandler(final AttributeDefinition attribute);

}
