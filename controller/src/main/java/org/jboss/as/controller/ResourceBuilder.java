/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.Set;

import org.jboss.as.controller.capability.Capability;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.registry.RuntimePackageDependency;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
public interface ResourceBuilder {
    ResourceBuilder setAddOperation(AbstractAddStepHandler handler);

    ResourceBuilder setAddOperation(RestartParentResourceAddHandler handler);

    ResourceBuilder setRemoveOperation(AbstractRemoveStepHandler handler);

    ResourceBuilder setRemoveOperation(RestartParentResourceRemoveHandler handler);

    ResourceBuilder addReadWriteAttribute(AttributeDefinition attributeDefinition, OperationStepHandler reader, OperationStepHandler writer);

    ResourceBuilder addReadOnlyAttribute(AttributeDefinition attributeDefinition);

    ResourceBuilder setAttributeResolver(ResourceDescriptionResolver resolver);

    ResourceBuilder addOperation(OperationDefinition operationDefinition, OperationStepHandler handler);

    ResourceBuilder pushChild(final PathElement pathElement);

    ResourceBuilder pushChild(final PathElement pathElement, StandardResourceDescriptionResolver resolver);

    ResourceBuilder pushChild(PathElement pathElement, OperationStepHandler addHandler, OperationStepHandler removeHandler);

    ResourceBuilder pushChild(PathElement pathElement, StandardResourceDescriptionResolver resolver, OperationStepHandler addHandler, OperationStepHandler removeHandler);

    ResourceBuilder pushChild(ResourceBuilder child);

    ResourceBuilder pop();

    ResourceBuilder addReadWriteAttributes(OperationStepHandler reader, OperationStepHandler writer, AttributeDefinition... attributes);

    ResourceBuilder addMetric(AttributeDefinition attributeDefinition, OperationStepHandler handler);

    ResourceBuilder addMetrics(OperationStepHandler metricHandler, AttributeDefinition... attributes);

    ResourceBuilder addOperation(OperationDefinition operationDefinition, OperationStepHandler handler, boolean inherited);

    ResourceBuilder deprecated(ModelVersion since);

    ResourceBuilder setRuntime();

    ResourceBuilder noFeature();

    ResourceBuilder addCapability(Capability capability);

    ResourceBuilder addCapabilities(Capability... capability);

    /**
     * Add additional packages to be provisioned.
     * Find more on additional packages in {@link org.jboss.as.controller.ResourceDefinition#registerAdditionalRuntimePackages}
     * @param packages The runtime packages to add.
     * @return This builder.
     */
    ResourceBuilder addAdditionalRuntimePackages(RuntimePackageDependency... packages);

    default ResourceBuilder setIncorporatingCapabilities(Set<RuntimeCapability> incorporating) {
        // We have a default implementation so unknown impls can compile, but if anyone calls
        // this against those unknown impls, that will fail. The expectation is there are no
        // such impls.
        // TODO remove this default impl at some point
        throw new UnsupportedOperationException();
    }

    default ResourceBuilder setRequirements(Set<CapabilityReferenceRecorder> requirements) {
        throw new UnsupportedOperationException();
    }

    ResourceDefinition build();

    class Factory {
        public static ResourceBuilder create(PathElement pathElement, StandardResourceDescriptionResolver resourceDescriptionResolver) {
            return ResourceBuilderRoot.create(pathElement, resourceDescriptionResolver);
        }

        public static ResourceBuilder createSubsystemRoot(PathElement pathElement,
                                                          StandardResourceDescriptionResolver resolver,
                                                          OperationStepHandler addHandler,
                                                          OperationStepHandler removeHandler) {
            return createSubsystemRoot(pathElement, resolver, addHandler, removeHandler, GenericSubsystemDescribeHandler.INSTANCE);
        }

        public static ResourceBuilder createSubsystemRoot(PathElement pathElement,
                                                          StandardResourceDescriptionResolver resolver,
                                                          OperationStepHandler addHandler,
                                                          OperationStepHandler removeHandler,
                                                          OperationStepHandler describeHandler) {
            ResourceBuilder builder = ResourceBuilderRoot.create(pathElement, resolver, addHandler, removeHandler);
            builder.addOperation(GenericSubsystemDescribeHandler.DEFINITION, describeHandler); //operation description is always the same
            return builder;
        }
    }
}
