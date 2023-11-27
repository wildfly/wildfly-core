/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.operation;

import java.util.Optional;
import java.util.function.Predicate;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.dmr.ModelNode;

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
    default Predicate<ModelNode> getCapabilityFilter(RuntimeCapability<?> capability) {
        return ModelNode::isDefined;
    }
}
