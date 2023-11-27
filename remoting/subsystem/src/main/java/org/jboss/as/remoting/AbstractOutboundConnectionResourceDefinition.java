/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.network.OutboundConnection;

/**
 * @author Jaikiran Pai
 */
abstract class AbstractOutboundConnectionResourceDefinition extends SimpleResourceDefinition {

    static final RuntimeCapability<Void> OUTBOUND_CONNECTION_CAPABILITY = RuntimeCapability.Builder.of(OutboundConnection.SERVICE_DESCRIPTOR)
                    .addRequirements(Capabilities.REMOTING_ENDPOINT_CAPABILITY_NAME)
                    .build();

    protected AbstractOutboundConnectionResourceDefinition(final Parameters parameters) {
        super(parameters.addCapabilities(OUTBOUND_CONNECTION_CAPABILITY));
    }
}
