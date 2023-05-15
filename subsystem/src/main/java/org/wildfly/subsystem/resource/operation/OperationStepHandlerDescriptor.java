/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.operation;

import java.util.Collections;
import java.util.Map;
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
     * Returns the runtime handling for this resource.
     * @return a runtime handler
     */
    default Optional<ResourceOperationRuntimeHandler> getRuntimeHandler() {
        return Optional.empty();
    }

    /**
     * The capabilities provided by this resource, paired with the condition under which they should be [un]registered
     * @return a map of capabilities to predicates
     */
    default Map<RuntimeCapability<?>, Predicate<ModelNode>> getCapabilities() {
        return Collections.emptyMap();
    }
}
