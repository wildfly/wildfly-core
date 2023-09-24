/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain.impl;

import java.io.Serializable;
import java.util.UUID;

import org.jboss.as.controller.client.logging.ControllerClientLogger;
import org.jboss.as.controller.client.helpers.domain.ServerIdentity;
import org.jboss.as.controller.client.helpers.domain.ServerUpdateResult;
import org.jboss.dmr.ModelNode;

/**
 * Default implementation of {@link ServerUpdateResult}.
 *
 * @author Brian Stansberry
 */
class ServerUpdateResultImpl implements ServerUpdateResult, Serializable {

    private static final long serialVersionUID = 5879115765933810032L;

    private final UUID actionId;
    private final ServerIdentity serverId;
    private final UpdateResultHandlerResponse urhr;
    private UpdateResultHandlerResponse rollbackResult;

    ServerUpdateResultImpl(final UUID actionId, final ServerIdentity serverId, final UpdateResultHandlerResponse urhr) {
        assert actionId != null : "actionId is null";
        assert serverId != null : "serverId is null";
        assert urhr != null : "urhr is null";
        this.actionId = actionId;
        this.serverId = serverId;
        this.urhr = urhr;
    }

    @Override
    public Throwable getFailureResult() {
        return urhr.getFailureResult();
    }

    @Override
    public ServerIdentity getServerIdentity() {
        return serverId;
    }

    @Override
    public ModelNode getSuccessResult() {
        return urhr.getSuccessResult();
    }

    @Override
    public UUID getUpdateActionId() {
        return actionId;
    }

    @Override
    public boolean isCancelled() {
        return urhr.isCancelled();
    }

    @Override
    public boolean isRolledBack() {
        return rollbackResult != null || urhr.isRolledBack();
    }

    @Override
    public boolean isTimedOut() {
        return urhr.isTimedOut();
    }

    void markRolledBack(UpdateResultHandlerResponse rollbackResult) {
        this.rollbackResult = rollbackResult;
    }

    @Override
    public boolean isServerRestarted() {
        return urhr.isServerRestarted();
    }

    @Override
    public Throwable getRollbackFailure() {
        if (rollbackResult == null) {
            return null;
        }
        else if (rollbackResult.isCancelled()) {
            return ControllerClientLogger.ROOT_LOGGER.rollbackCancelled();
        }
        else if (rollbackResult.isRolledBack()) {
            return ControllerClientLogger.ROOT_LOGGER.rollbackRolledBack();
        }
        else if (rollbackResult.isTimedOut()) {
            return ControllerClientLogger.ROOT_LOGGER.rollbackTimedOut();
        }
        else {
            return rollbackResult.getFailureResult();
        }
    }

}
