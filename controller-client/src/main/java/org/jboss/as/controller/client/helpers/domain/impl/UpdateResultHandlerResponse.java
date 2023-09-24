/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain.impl;

import java.io.Serializable;

import org.jboss.as.controller.client.logging.ControllerClientLogger;
import org.jboss.as.controller.client.helpers.domain.UpdateFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Encapsulates the possible values that can be passed to an
 * {@link UpdateResultHandler}'s callback methods.
 *
 * @author Brian Stansberry
 */
public class UpdateResultHandlerResponse implements Serializable {

    private static final long serialVersionUID = -5250735019112151634L;

    private final ModelNode successResult;
    private final Throwable failureResult;
    private final boolean cancelled;
    private final boolean timedOut;
    private final boolean rolledBack;
    private final boolean restarted;
    private final boolean rollbackCancelled;
    private final boolean rollbackTimedOut;
    private final Throwable rollbackFailure;

    public static UpdateResultHandlerResponse fromModelNode(ModelNode modelNode) {
        String outcome = modelNode.hasDefined("outcome") ? modelNode.get("outcome").asString() : ControllerClientLogger.ROOT_LOGGER.failed();
        UpdateResultHandlerResponse result;
        if ("success".equals(outcome)) {
            result = createSuccessResponse(modelNode.get("result"));
        }
        else if ("cancelled".equals(outcome)) {
            return createCancellationResponse();
        }
        else {
            String message = modelNode.hasDefined("failure-description") ? modelNode.get("failure-description").toString() : ControllerClientLogger.ROOT_LOGGER.noFailureDetails();
            result = createFailureResponse(new UpdateFailedException(message));
        }
        if (modelNode.get("rolled-back").asBoolean(false)) {
            result = createRollbackResponse(result);
        }
        else if (modelNode.hasDefined("rollback-failure-description")) {
            String message = modelNode.get("rollback-failure-description").toString();
            result = createRollbackFailedResponse(result, new UpdateFailedException(message));
        }
        return result;
    }
    public static  UpdateResultHandlerResponse createSuccessResponse(ModelNode result) {
        return new UpdateResultHandlerResponse(result, null, false, false, false, false, false, false, null);
    }

    public static  UpdateResultHandlerResponse createFailureResponse(Throwable cause) {
        return new UpdateResultHandlerResponse(null, cause, false, false, false, false, false, false, null);
    }

    public static  UpdateResultHandlerResponse createCancellationResponse() {
        return new UpdateResultHandlerResponse(null, null, true, false, false, false, false, false, null);
    }

    public static  UpdateResultHandlerResponse createTimeoutResponse() {
        return new UpdateResultHandlerResponse(null, null, false, true, false, false, false, false, null);
    }

    public static  UpdateResultHandlerResponse createRollbackResponse(UpdateResultHandlerResponse rolledBack) {
        return new UpdateResultHandlerResponse(rolledBack.successResult, rolledBack.failureResult,
                rolledBack.cancelled, rolledBack.timedOut, rolledBack.restarted, true, false, false, null);
    }

    public static  UpdateResultHandlerResponse createRollbackCancelledResponse(UpdateResultHandlerResponse rolledBack) {
        return new UpdateResultHandlerResponse(rolledBack.successResult, rolledBack.failureResult,
                rolledBack.cancelled, rolledBack.timedOut, rolledBack.restarted, false, true, false, null);
    }

    public static  UpdateResultHandlerResponse createRollbackTimedOutResponse(UpdateResultHandlerResponse rolledBack) {
        return new UpdateResultHandlerResponse(rolledBack.successResult, rolledBack.failureResult,
                rolledBack.cancelled, rolledBack.timedOut, rolledBack.restarted, false, false, true, null);
    }

    public static  UpdateResultHandlerResponse createRollbackFailedResponse(UpdateResultHandlerResponse rolledBack, Throwable cause) {
        return new UpdateResultHandlerResponse(rolledBack.successResult, rolledBack.failureResult,
                rolledBack.cancelled, rolledBack.timedOut, rolledBack.restarted, false, false, false, cause);
    }

    public static  UpdateResultHandlerResponse createRestartResponse() {
        return new UpdateResultHandlerResponse(null, null, false, false, true, false, false, false, null);
    }

    private UpdateResultHandlerResponse(final ModelNode successResult, final Throwable failureResult,
            final boolean cancelled, final boolean timedOut,
            final boolean restarted, final boolean rolledBack,
            final boolean rollbackCancelled, final boolean rollbackTimedOut,
            final Throwable rollbackFailure) {
        this.successResult = successResult;
        this.failureResult = failureResult;
        this.cancelled = cancelled;
        this.timedOut = timedOut;
        this.restarted = restarted;
        this.rolledBack = rolledBack;
        this.rollbackCancelled = rollbackCancelled;
        this.rollbackTimedOut = rollbackTimedOut;
        this.rollbackFailure = rollbackFailure;
    }


    public boolean isRollbackCancelled() {
        return rollbackCancelled;
    }

    public boolean isRollbackTimedOut() {
        return rollbackTimedOut;
    }

    public Throwable getRollbackFailure() {
        return rollbackFailure;
    }

    public ModelNode getSuccessResult() {
        return successResult;
    }

    public Throwable getFailureResult() {
        return failureResult;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public boolean isRolledBack() {
        return rolledBack;
    }

    public boolean isServerRestarted() {
        return restarted;
    }

}
