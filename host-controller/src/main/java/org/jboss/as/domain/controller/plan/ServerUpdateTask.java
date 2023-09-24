/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.plan;

import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.dmr.ModelNode;

/**
 * Base class for tasks that can perform an update on a server.
 *
 * @author Brian Stansberry
 */
abstract class ServerUpdateTask {

    protected final ServerUpdatePolicy updatePolicy;
    protected final ServerIdentity serverId;

    /**
     * Create a new update task.
     *  @param serverId the id of the server being updated. Cannot be <code>null</code>
     * @param updatePolicy the policy that controls whether the updates should be applied. Cannot be <code>null</code>
     */
    ServerUpdateTask(final ServerIdentity serverId,
                     final ServerUpdatePolicy updatePolicy) {
        assert serverId != null : "serverId is null";
        assert updatePolicy != null : "updatePolicy is null";
        this.serverId = serverId;
        this.updatePolicy = updatePolicy;
    }

    public abstract ModelNode getOperation();

    public ServerIdentity getServerIdentity() {
        return serverId;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{server=" + serverId.getServerName() + "}";
    }
}
