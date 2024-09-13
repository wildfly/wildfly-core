/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Resolves a value from a resource model.
 */
public interface ResourceModelResolver<T> extends ResourceResolver<T> {

    @Override
    default T resolve(OperationContext context, Resource resource) throws OperationFailedException {
        return this.resolve(context, resource.getModel());
    }

    /**
     * Resolves a value from the specified resource model, using the specified operation context.
     * @param context an operation context
     * @param model a resource model
     * @return the resolved value
     * @throws OperationFailedException if the value could not be resolved
     */
    T resolve(OperationContext context, ModelNode model) throws OperationFailedException;
}
