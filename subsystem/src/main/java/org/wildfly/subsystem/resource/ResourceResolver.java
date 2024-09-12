/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;

/**
 * Resolves a value from a resource.
 */
public interface ResourceResolver<T> {

    /**
     * Resolves a value from the specified resource, using the specified operation context.
     * @param context an operation context
     * @param resource a resource
     * @return the resolved value
     * @throws OperationFailedException if the value could not be resolved
     */
    T resolve(OperationContext context, Resource resource) throws OperationFailedException;
}
