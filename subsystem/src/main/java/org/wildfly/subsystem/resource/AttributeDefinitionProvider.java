/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource;

import java.util.Collection;
import java.util.EnumSet;
import java.util.stream.Stream;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;

/**
 * Provider of an attribute definition.
 * Used to support enumeration of the attributes of a resource.
 * @author Paul Ferraro
 */
public interface AttributeDefinitionProvider {

    /**
     * Return the provided attribute definition
     * @return an attribute definition
     */
    AttributeDefinition getAttributeDefinition();

    /**
     * Convenience method returning the name of this attribute.
     * @return the attribute name
     */
    default String getName() {
        return this.getAttributeDefinition().getName();
    }

    /**
     * Convenience method resolving the value of this attribute from the specified model applying any default value.
     * @param resolver an expression resolver
     * @param model the resource model
     * @return the resolved value
     * @throws OperationFailedException if the value was not valid
     */
    default ModelNode resolveModelAttribute(ExpressionResolver resolver, ModelNode model) throws OperationFailedException {
        return this.getAttributeDefinition().resolveModelAttribute(resolver, model);
    }

    /**
     * Convenience method that exposes an enumeration of attribute definition providers as a stream of {@link AttributeDefinition} instances.
     * @param <E> the attribute enum type
     * @param enumClass the enum class
     * @return a stream of attribute definitions.
     */
    static <E extends Enum<E> & AttributeDefinitionProvider> Stream<AttributeDefinition> stream(Class<E> enumClass) {
        return stream(EnumSet.allOf(enumClass));
    }

    /**
     * Convenience method that exposes a collection of attribute definition providers as a stream of {@link AttributeDefinition} instances.
     * @return a stream of attribute definitions.
     */
    static Stream<AttributeDefinition> stream(Collection<? extends AttributeDefinitionProvider> attributes) {
        return attributes.stream().map(AttributeDefinitionProvider::getAttributeDefinition);
    }
}
