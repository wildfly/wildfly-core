/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Base class for {@link OperationStepHandler} implementations for updating an existing managed resource.
 *
 * @author Emanuel Muckenhuber
 */
public abstract class AbstractModelUpdateHandler implements OperationStepHandler {

    /** {@inheritDoc */
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        final Resource resource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        updateModel(operation, resource);

        if (requiresRuntime(context)) {
            context.addStep(new OperationStepHandler() {
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    performRuntime(context, operation, resource);

                    context.completeStep(new OperationContext.RollbackHandler() {
                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            rollbackRuntime(context, operation, resource);
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    /**
     * Update the given resource in the persistent configuration model based on the values in the given operation.
     *
     * @param operation the operation
     * @param resource the resource that corresponds to the address of {@code operation}
     *
     * @throws OperationFailedException if {@code operation} is invalid or populating the model otherwise fails
     */
    protected void updateModel(final ModelNode operation, final Resource resource) throws  OperationFailedException {
        updateModel(operation, resource.getModel());
    }

    /**
     * Update the given node in the persistent configuration model based on the values in the given operation.
     *
     * @param operation the operation
     * @param model persistent configuration model node that corresponds to the address of {@code operation}
     *
     * @throws OperationFailedException if {@code operation} is invalid or populating the model otherwise fails
     */
    protected abstract void updateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException;

    /**
     * Gets whether {@link #performRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}}
     * should be called.
     *
     * @param context operation context
     * @return {@code true} if {@code performRuntime} should be invoked; {@code false} otherwise.
     */
    protected boolean requiresRuntime(OperationContext context) {
        return context.isNormalServer();
    }

    /**
     * Make any runtime changes necessary to effect the changes indicated by the given {@code operation}. Executes
     * after {@link #updateModel(org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)}, so the given {@code model}
     * parameter will reflect any changes made in that method.
     * <p>
     * This default implementation does nothing.
     * </p>
     *
     * @param context  the operation context
     * @param operation the operation being executed
     * @param resource the resource that corresponds to the address of {@code operation}
     * @throws OperationFailedException if {@code operation} is invalid or updating the runtime otherwise fails
     */
    protected void performRuntime(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {
    }

    /**
     * Rollback runtime changes made in {@link #performRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource)}.
     * <p>
     * This default implementation removes all services in the given list of {@code controllers}. The contents of
     * {@code controllers} is the same as what was in the {@code newControllers} parameter passed to {@code performRuntime()}
     * when that method returned.
     * </p>
     * @param context the operation context
     * @param operation the operation being executed
     * @param resource the resource that corresponds to the address of {@code operation}
     */
    protected void rollbackRuntime(OperationContext context, final ModelNode operation, final Resource resource) {

    }

}
