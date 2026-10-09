/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.operation;

import java.util.Optional;
import java.util.function.BiPredicate;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.wildfly.service.capture.ValueRegistry;

/**
 * Describes common properties of all operation handlers of a resource.
 * @author Paul Ferraro
 */
public interface OperationStepHandlerDescriptor {

    /**
     * Returns the optional runtime handling for this resource.
     * @return an optional runtime handler
     */
    default Optional<ResourceOperationRuntimeHandler> getRuntimeHandler() {
        return Optional.empty();
    }

    /**
     * Returns the resource model filter used to determine whether the specified capability should be [un]registered.
     * @param capability a runtime capability
     * @return a resource model predicate
     */
    default BiPredicate<OperationContext, Resource> getCapabilityFilter(RuntimeCapability<?> capability) {
        return (context, resource) -> resource.getModel().isDefined();
    }

    /**
     * Returns the value registry associated with the specified attribute, or null, if none exists.
     * @param attribute the definition of an attribute of this resource.
     * @return the value registry associated with the specified attribute, or null, if none exists.
     */
    default ValueRegistry<PathAddress, ModelNode> getValueRegistry(AttributeDefinition attribute) {
        return null;
    }
}
