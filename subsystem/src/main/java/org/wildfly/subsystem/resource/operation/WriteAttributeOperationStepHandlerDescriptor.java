/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.operation;

import java.util.Collection;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;

/**
 * Describes the properties of a resource {@code org.jboss.as.controller.descriptions.ModelDescriptionConstants#WRITE_ATTRIBUTE_OPERATION} operation handler.
 * @author Paul Ferraro
 */
public interface WriteAttributeOperationStepHandlerDescriptor extends OperationStepHandlerDescriptor {

    /**
     * Attributes of the add operation.
     * @return a collection of attributes
     */
    Collection<AttributeDefinition> getAttributes();

    /**
     * Attributes (not specified by {@link #getAttributes()}) will be ignored at runtime..
     * @return a collection of ignored attributes
     */
    default Collection<AttributeDefinition> getIgnoredAttributes() {
        return Set.of();
    }
}
