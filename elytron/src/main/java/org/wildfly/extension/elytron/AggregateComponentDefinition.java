/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;

import java.util.List;
import java.util.function.Function;


import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * A {@link ResourceDefinition} for components that are aggregations of the same type.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class AggregateComponentDefinition<T> extends SimpleResourceDefinition {

    private final ListAttributeDefinition aggregateReferences;
    private final OperationStepHandler attributeWriteHandler;

    private AggregateComponentDefinition(Class<T> classType, String pathKey, OperationStepHandler addHandler, OperationStepHandler removeHandler,
            ListAttributeDefinition aggregateReferences, OperationStepHandler attributeWriteHandler, RuntimeCapability<?> runtimeCapability) {
        super(new Parameters(PathElement.pathElement(pathKey), ElytronExtension.getResourceDescriptionResolver(pathKey))
            .setAddHandler(addHandler)
            .setRemoveHandler(removeHandler)
            .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilities(runtimeCapability));

        this.aggregateReferences = aggregateReferences;
        this.attributeWriteHandler = attributeWriteHandler;
    }

    ListAttributeDefinition getReferencesAttribute() {
        return aggregateReferences;
    }

    /**
     * @see org.jboss.as.controller.SimpleResourceDefinition#registerAttributes(org.jboss.as.controller.registry.ManagementResourceRegistration)
     */
    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(aggregateReferences, null, attributeWriteHandler);
    }

    static <T> AggregateComponentDefinition<T> create(Class<T> aggregationType, String componentName, String referencesName, RuntimeCapability<?> runtimeCapability, Function<T[], T> aggregator) {
        return create(aggregationType, componentName, referencesName, runtimeCapability, aggregator, true);
    }

    static <T> AggregateComponentDefinition<T> create(Class<T> aggregationType, String componentName, String referencesName,
            RuntimeCapability<?> runtimeCapability, Function<T[], T> aggregator, boolean dependOnProviderRegistration) {
        String capabilityName = runtimeCapability.getName();
        StringListAttributeDefinition aggregateReferences = new StringListAttributeDefinition.Builder(referencesName)
            .setMinSize(2)
            .setRequired(true)
            .setCapabilityReference(capabilityName, capabilityName)//todo this is ultra fishy
            .setRestartAllServices()
            .build();

        AbstractAddStepHandler add = new AggregateComponentAddHandler<T>(aggregationType, aggregator, aggregateReferences, runtimeCapability, dependOnProviderRegistration);
        OperationStepHandler remove = new TrivialCapabilityServiceRemoveHandler(add, runtimeCapability);
        OperationStepHandler write = new ElytronReloadRequiredWriteAttributeHandler(aggregateReferences);

        return new AggregateComponentDefinition<T>(aggregationType, componentName, add, remove, aggregateReferences, write, runtimeCapability);
    }

    private static class AggregateComponentAddHandler<T> extends BaseAddHandler {

        private final Class<T> aggregationType;
        private final Function<T[], T> aggregator;
        private final StringListAttributeDefinition aggregateReferences;
        private final RuntimeCapability<?> runtimeCapability;
        private final boolean dependOnProviderRegistration;

        private AggregateComponentAddHandler(Class<T> aggregationType, Function<T[], T> aggregator,
                StringListAttributeDefinition aggregateReferences, RuntimeCapability<?> runtimeCapability,
                boolean dependOnProviderRegistration) {
            super(runtimeCapability, aggregateReferences);
            this.aggregationType = aggregationType;
            this.aggregator = aggregator;
            this.aggregateReferences = aggregateReferences;
            this.runtimeCapability = runtimeCapability;
            this.dependOnProviderRegistration = dependOnProviderRegistration;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();
            RuntimeCapability<?> instanceRuntimeCapability = runtimeCapability.fromBaseCapability(context.getCurrentAddressValue());
            ServiceName componentName = instanceRuntimeCapability.getCapabilityServiceName(aggregationType);

            AggregateComponentService<T> aggregateComponentService = new AggregateComponentService<T>(aggregationType, aggregator);

            ServiceBuilder<T> serviceBuilder = serviceTarget.addService(componentName, aggregateComponentService);

            List<String> aggregates = aggregateReferences.unwrap(context, model);

            String baseCapabilityName = runtimeCapability.getName();
            for (String current : aggregates) {
                String runtimeCapabilityName = RuntimeCapability.buildDynamicCapabilityName(baseCapabilityName, current);
                ServiceName realmServiceName = context.getCapabilityServiceName(runtimeCapabilityName, aggregationType);

                serviceBuilder.addDependency(realmServiceName, aggregationType, aggregateComponentService.newInjector());
            }

            commonDependencies(serviceBuilder, true, dependOnProviderRegistration)
                .setInitialMode(Mode.LAZY)
                .install();
        }

    }

}
