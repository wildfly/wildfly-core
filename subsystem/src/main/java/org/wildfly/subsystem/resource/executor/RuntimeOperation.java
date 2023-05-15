/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.executor;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Defines the behavior of a runtime operation.
 * @param <C> operation context
 * @author Paul Ferraro
 */
public interface RuntimeOperation<C> {

    /**
     * Returns the operation definition for this runtime operation.
     * @return an operation definition
     */
    OperationDefinition getOperationDefinition();

    /**
     * Convenience method that return the name of this runtime operation.
     * @return the operation name
     */
    default String getName() {
        return this.getOperationDefinition().getName();
    }

    /**
     * Execute against the specified context.
     * @param resolver an expression resolver
     * @param operation original operation model to resolve parameters from
     * @param context an execution context
     * @return the execution result (possibly null).
     */
    ModelNode execute(ExpressionResolver resolver, ModelNode operation, C context) throws OperationFailedException;
}
