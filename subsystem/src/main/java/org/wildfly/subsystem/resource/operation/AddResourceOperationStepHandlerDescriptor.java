/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.operation;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.wildfly.subsystem.resource.AttributeTranslation;

/**
 * Describes the properties of a resource {@value org.jboss.as.controller.descriptions.ModelDescriptionConstants#ADD} operation handler.
 * @author Paul Ferraro
 */
public interface AddResourceOperationStepHandlerDescriptor extends OperationStepHandlerDescriptor {

    /**
     * Returns the required child resources for this resource description.
     * @return a collection of resource paths
     * @deprecated Superseded by {@link #getRequiredChildResources()}.
     */
    @Deprecated(forRemoval = true, since = "26.0.0")
    default Set<PathElement> getRequiredChildren() {
        return this.getRequiredChildResources().keySet();
    }

    /**
     * Returns the required child resources for this resource description.
     * @return a collection of resource paths
     */
    default Map<PathElement, ResourceRegistration> getRequiredChildResources() {
        return Map.of();
    }

    /**
     * Returns the required singleton child resources for this resource description.
     * This means only one child resource should exist for the given child type.
     * @return a collection of resource paths
     * @deprecated Superseded by {@link #getRequiredSingletonChildResources()}
     */
    @Deprecated(forRemoval = true, since = "26.0.0")
    default Set<PathElement> getRequiredSingletonChildren() {
        return this.getRequiredSingletonChildResources().keySet();
    }

    /**
     * Returns the required singleton child resources for this resource description.
     * This means only one child resource should exist for the given child type.
     * @return a collection of resource paths
     */
    default Map<PathElement, ResourceRegistration> getRequiredSingletonChildResources() {
        return Map.of();
    }

    /**
     * Returns the attribute translation for the specified attribute, or null if none exists
     * @return an attribute translation, or null if none exists
     */
    default AttributeTranslation getAttributeTranslation(AttributeDefinition attribute) {
        return null;
    }

    /**
     * Returns a transformation for a newly created resource.
     * @return a resource transformation
     */
    default UnaryOperator<Resource> getResourceTransformation() {
        return UnaryOperator.identity();
    }

    /**
     * Returns an optional consumer of a {@link DeploymentProcessorTarget}, used to add deployment unit processors to the deployment chain.
     * @return an optional {@link DeploymentProcessorTarget} consumer
     */
    default Optional<Consumer<DeploymentProcessorTarget>> getDeploymentChainContributor() {
        return Optional.empty();
    }
}
