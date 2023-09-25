/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.standalone;

import java.util.UUID;

import org.wildfly.common.Assert;

/**
 * Abstract superclass of implementations of {@link ServerUpdateActionResult}.
 *
 * @author Brian Stansberry
 */
public abstract class AbstractServerUpdateActionResult<T extends ServerUpdateActionResult> implements ServerUpdateActionResult, java.io.Serializable {

    private static final long serialVersionUID = -4692787126053225682L;

    private final UUID id;
    private Result result;
    private final Throwable deploymentException;
    private T rollbackResult;

    protected AbstractServerUpdateActionResult() {
        this.id = null;
        this.deploymentException = null;
    }

    public AbstractServerUpdateActionResult(UUID id, Result result) {
        this(id, result, null);
    }

    public AbstractServerUpdateActionResult(UUID id, Throwable deploymentException) {
        this(id, Result.FAILED, deploymentException);
    }

    public AbstractServerUpdateActionResult(UUID id, Result result, Throwable deploymentException) {
        Assert.checkNotNullParam("id", id);
        Assert.checkNotNullParam("result", result);
        this.id = id;
        this.result = result;
        this.deploymentException = deploymentException;
    }

    @Override
    public UUID getUpdateActionId() {
        return id;
    }

    @Override
    public Throwable getDeploymentException() {
        return deploymentException;
    }

    @Override
    public Result getResult() {
        if (rollbackResult != null) {
            return result == Result.FAILED ? result : Result.ROLLED_BACK;
        }
        return result;
    }

    @Override
    public T getRollbackResult() {
        return rollbackResult;
    }

    protected abstract Class<T> getRollbackResultClass();

    public static <R  extends ServerUpdateActionResult> void installRollbackResult(AbstractServerUpdateActionResult<R> update, ServerUpdateActionResult rollback) {
        R cast = update.getRollbackResultClass().cast(rollback);
        update.rollbackResult = cast;
    }
}
