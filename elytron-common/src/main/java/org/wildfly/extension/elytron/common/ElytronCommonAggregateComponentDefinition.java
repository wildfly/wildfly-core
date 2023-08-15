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
package org.wildfly.extension.elytron.common;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
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
import org.jboss.msc.service.StartException;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.common.function.ExceptionSupplier;

/**
 * A {@link ResourceDefinition} for components that are aggregations of the same type.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:carodrig@redhat.com">Cameron Rodriguez</a>
 */
public class ElytronCommonAggregateComponentDefinition<T> extends SimpleResourceDefinition {

    private final ListAttributeDefinition aggregateReferences;
    private final OperationStepHandler attributeWriteHandler;

    protected ElytronCommonAggregateComponentDefinition(final Class<?> extensionClass, Class<T> classType, String pathKey, OperationStepHandler addHandler, OperationStepHandler removeHandler,
            ListAttributeDefinition aggregateReferences, OperationStepHandler attributeWriteHandler, RuntimeCapability<?> runtimeCapability) {
        super(new Parameters(PathElement.pathElement(pathKey),
                ElytronCommonDefinitions.getResourceDescriptionResolver(extensionClass, pathKey))
            .setAddHandler(addHandler)
            .setRemoveHandler(removeHandler)
            .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilities(runtimeCapability));

        this.aggregateReferences = aggregateReferences;
        this.attributeWriteHandler = attributeWriteHandler;
    }

    public ListAttributeDefinition getReferencesAttribute() {
        return aggregateReferences;
    }

    /**
     * @see org.jboss.as.controller.SimpleResourceDefinition#registerAttributes(org.jboss.as.controller.registry.ManagementResourceRegistration)
     */
    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(aggregateReferences, null, attributeWriteHandler);
    }

    public static <T> ElytronCommonAggregateComponentDefinition<T> createCommonDefinition(final Class<?> extensionClass, Class<T> aggregationType,
            String componentName, String referencesName, RuntimeCapability<?> runtimeCapability, Function<T[], T> aggregator) {
        return createCommonDefinition(extensionClass, aggregationType, componentName, referencesName, runtimeCapability, aggregator, true);
    }

    public static <T> ElytronCommonAggregateComponentDefinition<T> createCommonDefinition(final Class<?> extensionClass, Class<T> aggregationType,
            String componentName, String referencesName, RuntimeCapability<?> runtimeCapability, Function<T[], T> aggregator, boolean dependOnProviderRegistration) {
        String capabilityName = runtimeCapability.getName();
        StringListAttributeDefinition aggregateReferences = new StringListAttributeDefinition.Builder(referencesName)
            .setMinSize(2)
            .setRequired(true)
            .setCapabilityReference(capabilityName, capabilityName)//todo this is ultra fishy
            .setRestartAllServices()
            .build();

        AbstractAddStepHandler add = new AggregateComponentAddHandler<T>(extensionClass, aggregationType, aggregator, aggregateReferences, runtimeCapability, dependOnProviderRegistration);
        OperationStepHandler remove = new TrivialCapabilityServiceRemoveHandler(extensionClass, add, runtimeCapability);
        OperationStepHandler write = new ElytronReloadRequiredWriteAttributeHandler(aggregateReferences);

        return new ElytronCommonAggregateComponentDefinition<T>(extensionClass, aggregationType, componentName, add, remove, aggregateReferences, write, runtimeCapability);
    }

   public static <T> ElytronCommonAggregateComponentDefinition<T> createCommonDefinition(final Class<?> extensionClass, Class<T> aggregationType, String componentName,
            String referencesName, RuntimeCapability<?> runtimeCapability, String apiCapabilityName, Function<T[], T> aggregator, boolean dependOnProviderRegistration) {
        String capabilityName = runtimeCapability.getName();
        StringListAttributeDefinition aggregateReferences = new StringListAttributeDefinition.Builder(referencesName)
            .setMinSize(2)
            .setRequired(true)
            .setCapabilityReference(capabilityName, capabilityName)//todo this is ultra fishy
            .setRestartAllServices()
            .build();

        AbstractAddStepHandler add = new AggregateApiComponentAddHandler<T>(extensionClass, aggregationType, aggregator, aggregateReferences, runtimeCapability, apiCapabilityName, dependOnProviderRegistration);
        OperationStepHandler remove = new TrivialCapabilityServiceRemoveHandler(extensionClass, add, runtimeCapability);
        OperationStepHandler write = new ElytronReloadRequiredWriteAttributeHandler(aggregateReferences);

        return new ElytronCommonAggregateComponentDefinition<T>(extensionClass, aggregationType, componentName, add, remove, aggregateReferences, write, runtimeCapability);
    }

    protected static class AggregateComponentAddHandler<T> extends ElytronCommonBaseAddHandler {

        private final Class<?> extensionClass;
        private final Class<T> aggregationType;
        private final Function<T[], T> aggregator;
        private final StringListAttributeDefinition aggregateReferences;
        private final RuntimeCapability<?> runtimeCapability;
        private final boolean dependOnProviderRegistration;

        protected AggregateComponentAddHandler(final Class<?> extensionClass, Class<T> aggregationType, Function<T[], T> aggregator,
                StringListAttributeDefinition aggregateReferences, RuntimeCapability<?> runtimeCapability,
                boolean dependOnProviderRegistration) {
            super(runtimeCapability, aggregateReferences);
            this.extensionClass = extensionClass;
            this.aggregationType = aggregationType;
            this.aggregator = aggregator;
            this.aggregateReferences = aggregateReferences;
            this.runtimeCapability = runtimeCapability;
            this.dependOnProviderRegistration = dependOnProviderRegistration;
        }

        @Override
        protected String getSubsystemCapability() {
            return ElytronCommonDefinitions.getSubsystemCapability(extensionClass);
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

            ElytronCommonDefinitions.commonRequirements(extensionClass, serviceBuilder, true, dependOnProviderRegistration)
                .setInitialMode(Mode.LAZY)
                .install();
        }
    }

    protected static class AggregateApiComponentAddHandler<T> extends ElytronCommonDoohickeyAddHandler<T> {

        private final Class<T> aggregationType;
        private final Function<T[], T> aggregator;
        private final StringListAttributeDefinition aggregateReferences;
        private final RuntimeCapability<?> runtimeCapability;
        private final boolean dependOnProviderRegistration;
        private final String apiCapabilityName;

        protected AggregateApiComponentAddHandler(final Class<?> extensionClass, Class<T> aggregationType, Function<T[], T> aggregator,
                StringListAttributeDefinition aggregateReferences, RuntimeCapability<?> runtimeCapability, String apiCapabilityName,
                boolean dependOnProviderRegistration) {
            super(extensionClass, runtimeCapability, new AttributeDefinition[] { aggregateReferences }, apiCapabilityName);
            this.aggregationType = aggregationType;
            this.aggregator = aggregator;
            this.aggregateReferences = aggregateReferences;
            this.runtimeCapability = runtimeCapability;
            this.dependOnProviderRegistration = dependOnProviderRegistration;
            this.apiCapabilityName = apiCapabilityName;
         }

        @Override
        protected ElytronDoohickey<T> createDoohickey(PathAddress resourceAddress) {
            return new ElytronDoohickey<T>(resourceAddress) {

                private volatile List<String> aggregates;

                @Override
                protected void resolveRuntime(ModelNode model, OperationContext context) throws OperationFailedException {
                    aggregates = aggregateReferences.unwrap(context, model);
                }

                @Override
                protected ExceptionSupplier<T, StartException> prepareServiceSupplier(OperationContext context,
                        CapabilityServiceBuilder<?> serviceBuilder) throws OperationFailedException {

                    final List<Supplier<T>> typeSuppliers = new ArrayList<>(aggregates.size());
                    for (String current : aggregates) {
                        String capabilityName = RuntimeCapability.buildDynamicCapabilityName(runtimeCapability.getName(), current);
                        ServiceName serviceName = context.getCapabilityServiceName(capabilityName, aggregationType);

                        typeSuppliers.add(serviceBuilder.requires(serviceName));
                    }

                    return new ExceptionSupplier<T, StartException>() {

                        @Override
                        public T get() throws StartException {
                            List<T> loadedTypes = new ArrayList<>(typeSuppliers.size());
                            for (Supplier<T> current : typeSuppliers) {
                                loadedTypes.add(current.get());
                            }

                            return aggregator.apply(loadedTypes.toArray((T[]) Array.newInstance(aggregationType, loadedTypes.size())));
                        }
                    };
                }

                @Override
                protected T createImmediately(OperationContext foreignContext) throws OperationFailedException {
                    List<T> loadedTypes = new ArrayList<>(aggregates.size());
                    for (String current : aggregates) {
                        final ExceptionFunction<OperationContext, T, OperationFailedException> typeApi = foreignContext
                                .getCapabilityRuntimeAPI(apiCapabilityName, current, ExceptionFunction.class);
                        loadedTypes.add(typeApi.apply(foreignContext));
                    }

                    return aggregator.apply(loadedTypes.toArray((T[]) Array.newInstance(aggregationType, loadedTypes.size())));
                }

            };
        }

        @Override
        protected boolean dependOnProviderRegistration() {
            return dependOnProviderRegistration;
        }

        @Override
        protected Mode getInitialMode() {
            return Mode.LAZY;
        }
    }

}
