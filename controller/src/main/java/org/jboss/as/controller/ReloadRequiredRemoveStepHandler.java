/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.dmr.ModelNode;

/**
 * A handler for the "remove" operation that always puts the process in "reload-required" state.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ReloadRequiredRemoveStepHandler extends AbstractRemoveStepHandler {

    public static final ReloadRequiredRemoveStepHandler INSTANCE = new ReloadRequiredRemoveStepHandler();

    /**
     * Creates a new {@code ReloadRequiredRemoveStepHandler}
     */
    public ReloadRequiredRemoveStepHandler() {
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        context.reloadRequired();
    }

    @Override
    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        context.revertReloadRequired();
    }
}
