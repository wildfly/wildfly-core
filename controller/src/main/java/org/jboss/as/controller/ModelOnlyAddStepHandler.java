/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * A handler for the {@code add} operation that only manipulates the model.  The original expected use is for
 * resources that have been dropped from recent versions, but for which configuration manageablity is retained in
 * order to allow use on legacy hosts in a managed domain. This handler would be used on the host controllers for
 * the newer version nodes (particularly the master host controller.)
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
public class ModelOnlyAddStepHandler extends AbstractAddStepHandler {

    public static final OperationStepHandler INSTANCE = new ModelOnlyAddStepHandler();

    protected ModelOnlyAddStepHandler() {
    }

    /**
     * @deprecated Use {@link #INSTANCE} instead.
     */
    @Deprecated(forRemoval = true)
    public ModelOnlyAddStepHandler(AttributeDefinition... attributes) {
        super(attributes);
    }

    /**
     * @deprecated Use {@link #INSTANCE} instead.
     */
    @Deprecated(forRemoval = true)
    public ModelOnlyAddStepHandler(Parameters parameters) {
        super(parameters);
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

    @Override
    protected final void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        throw new IllegalStateException();
    }

    @Override
    protected final void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {
        throw new IllegalStateException();
    }
}
