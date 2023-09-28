/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.dmr.ModelNode;

/**
 * A handler for the {@code remove} operation that only manipulates the model. The original expected use is for
 * resources that have been dropped from recent versions, but for which configuration manageablity is retained in
 * order to allow use on legacy hosts in a managed domain. This handler would be used on the host controllers for
 * the newer version nodes (particularly the master host controller.)
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class ModelOnlyRemoveStepHandler extends AbstractRemoveStepHandler {

    public static final ModelOnlyRemoveStepHandler INSTANCE = new ModelOnlyRemoveStepHandler();

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
        throw new IllegalStateException();
    }

    @Override
    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        throw new IllegalStateException();
    }

    /**
     * Returns {@code false}.
     *
     * {@inheritDoc}
     */
    @Override
    protected final boolean requiresRuntime(OperationContext context) {
        return false;
    }
}
