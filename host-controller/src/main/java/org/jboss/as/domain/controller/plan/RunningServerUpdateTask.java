/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.plan;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;

import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * {@link ServerUpdateTask} that performs the updates by applying them
 * to a running server.
 */
class RunningServerUpdateTask extends ServerUpdateTask {

    private final ModelNode serverUpdate;

    /**
     * Constructor.
     *  @param serverId the id of the server being updated. Cannot be <code>null</code>
     * @param serverUpdate the actual rollback updates to apply to this server. Cannot be <code>null</code>
     * @param updatePolicy the policy that controls whether the updates should be applied. Cannot be <code>null</code>
     */
    RunningServerUpdateTask(final ServerIdentity serverId,
                            final ModelNode serverUpdate,
                            final ServerUpdatePolicy updatePolicy) {
        super(serverId, updatePolicy);
        this.serverUpdate = serverUpdate;
    }

    @Override
    public ModelNode getOperation() {
        return getServerOp();
    }

    private ModelNode getServerOp() {
        ModelNode op = serverUpdate.clone();
        ModelNode address = new ModelNode();
        address.add(HOST, serverId.getHostName());
        address.add(RUNNING_SERVER, serverId.getServerName());
        if (serverUpdate.hasDefined(OP_ADDR)) {
            for (Property prop : serverUpdate.get(OP_ADDR).asPropertyList()) {
                address.add(prop.getName(), prop.getValue().asString());
            }
        }
        op.get(OP_ADDR).set(address);
        return op;
    }
}
