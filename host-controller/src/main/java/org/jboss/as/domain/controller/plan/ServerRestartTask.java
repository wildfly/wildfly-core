/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.plan;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;

import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.dmr.ModelNode;

/**
 * {@link ServerUpdateTask} that performs the update by triggering a
 * restart of the server. The restart results in the server getting the current
 * model state.
 */
class ServerRestartTask extends ServerUpdateTask {

    private final long gracefulTimeout;

    ServerRestartTask(final ServerIdentity serverId,
                      final ServerUpdatePolicy updatePolicy,
                      final long gracefulTimeout) {
        super(serverId, updatePolicy);
        this.gracefulTimeout = gracefulTimeout;
    }

    @Override
    public ModelNode getOperation() {
        return getRestartOp();
    }

    private ModelNode getRestartOp() {
        ModelNode address = new ModelNode();
        address.add(HOST, serverId.getHostName());

        ModelNode op = Util.getEmptyOperation("restart-server", address);
        op.get(SERVER).set(serverId.getServerName());
        op.get("graceful-timeout").set(gracefulTimeout);
        return op;
    }
}
