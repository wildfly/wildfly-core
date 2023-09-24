/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.dmr.ModelNode;

/**
 * A handler that simply calls {@link OperationContext#completeStep(OperationContext.RollbackHandler)} with a
 * {@link OperationContext.RollbackHandler#NOOP_ROLLBACK_HANDLER no-op rollback handler}.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class NoopOperationStepHandler implements OperationStepHandler {

    /**
     * A {@code NoopOperationStepHandler} that also calls {@link OperationContext#getResult()} thus
     * initializing it to {@link org.jboss.dmr.ModelType#UNDEFINED}.
     */
    public static final NoopOperationStepHandler WITH_RESULT = new NoopOperationStepHandler(true);
    /**
     * A {@code NoopOperationStepHandler} that doesn't do anything to establish the operation result node.
     *
     * @see #WITH_RESULT
     */
    public static final NoopOperationStepHandler WITHOUT_RESULT = new NoopOperationStepHandler(false);

    private final boolean setResult;

    private NoopOperationStepHandler(boolean setResult) {
        this.setResult = setResult;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        if (setResult) {
            context.getResult();
        }
    }
}
