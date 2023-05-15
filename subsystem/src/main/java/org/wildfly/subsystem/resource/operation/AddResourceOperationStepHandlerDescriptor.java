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
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.wildfly.subsystem.resource.AttributeTranslation;

/**
 * Describes the properties of a resource {@value org.jboss.as.controller.descriptions.ModelDescriptionConstants#ADD} operation handler.
 * @author Paul Ferraro
 */
public interface AddResourceOperationStepHandlerDescriptor extends WriteAttributeOperationStepHandlerDescriptor, ResourceOperationStepHandlerDescriptor {

    /**
     * Returns the restart flag for the {@value org.jboss.as.controller.descriptions.ModelDescriptionConstants#ADD} operation of this resource.
     * @return an operation flag
     */
    default OperationEntry.Flag getAddOperationRestartFlag() {
        return OperationEntry.Flag.RESTART_NONE;
    }

    /**
     * Custom attributes of the add operation, processed using a specific write-attribute handler.
     * @return a map of attributes and their write-attribute handler
     */
    default Map<AttributeDefinition, OperationStepHandler> getCustomAttributes() {
        return Map.of();
    }

    /**
     * Returns the required child resources for this resource description.
     * @return a collection of resource paths
     */
    default Set<PathElement> getRequiredChildren() {
        return Set.of();
    }

    /**
     * Returns the required singleton child resources for this resource description.
     * This means only one child resource should exist for the given child type.
     * @return a collection of resource paths
     */
    default Set<PathElement> getRequiredSingletonChildren() {
        return Set.of();
    }

    /**
     * Returns a mapping of attribute translations
     * @return an attribute translation mapping
     */
    default Map<AttributeDefinition, AttributeTranslation> getAttributeTranslations() {
        return Map.of();
    }

    /**
     * Returns a transformer for the add operation handler.
     * This is typically used to adapt legacy operations to conform to the current version of the model.
     * @return an operation handler transformer.
     */
    default UnaryOperator<OperationStepHandler> getAddOperationTransformation() {
        return UnaryOperator.identity();
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
