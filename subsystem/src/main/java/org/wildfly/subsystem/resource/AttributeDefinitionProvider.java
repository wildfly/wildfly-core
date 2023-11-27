/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource;

import java.util.function.Supplier;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Provider of an attribute definition.
 * Used to support enumeration of the attributes of a resource.
 * @author Paul Ferraro
 */
public interface AttributeDefinitionProvider extends Supplier<AttributeDefinition> {

    /**
     * Convenience method returning the name of this attribute.
     * @return the attribute name
     */
    default String getName() {
        return this.get().getName();
    }

    /**
     * Convenience method resolving the value of this attribute from the specified model applying any default value.
     * @param resolver an expression resolver
     * @param model the resource model
     * @return the resolved value
     * @throws OperationFailedException if the value was not valid
     */
    default ModelNode resolveModelAttribute(ExpressionResolver resolver, ModelNode model) throws OperationFailedException {
        return this.get().resolveModelAttribute(resolver, model);
    }
}
