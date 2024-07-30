/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractAddStepHandler;
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

        return new AggregateComponentDefinition<T>(aggregationType, componentName, add, remove, aggregateReferences, ElytronReloadRequiredWriteAttributeHandler.INSTANCE, runtimeCapability);
    }

    static <T> AggregateComponentDefinition<T> create(Class<T> aggregationType, String componentName, String referencesName,
            RuntimeCapability<?> runtimeCapability, String apiCapabilityName, Function<T[], T> aggregator, boolean dependOnProviderRegistration) {
        String capabilityName = runtimeCapability.getName();
        StringListAttributeDefinition aggregateReferences = new StringListAttributeDefinition.Builder(referencesName)
            .setMinSize(2)
            .setRequired(true)
            .setCapabilityReference(capabilityName, capabilityName)//todo this is ultra fishy
            .setRestartAllServices()
            .build();

        AbstractAddStepHandler add = new AggregateApiComponentAddHandler<T>(aggregationType, aggregator, aggregateReferences, runtimeCapability, apiCapabilityName, dependOnProviderRegistration);
        OperationStepHandler remove = new TrivialCapabilityServiceRemoveHandler(add, runtimeCapability);

        return new AggregateComponentDefinition<T>(aggregationType, componentName, add, remove, aggregateReferences, ElytronReloadRequiredWriteAttributeHandler.INSTANCE, runtimeCapability);
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
            super(runtimeCapability);
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

    private static class AggregateApiComponentAddHandler<T> extends DoohickeyAddHandler<T> {

        private final Class<T> aggregationType;
        private final Function<T[], T> aggregator;
        private final StringListAttributeDefinition aggregateReferences;
        private final RuntimeCapability<?> runtimeCapability;
        private final boolean dependOnProviderRegistration;
        private final String apiCapabilityName;

        private AggregateApiComponentAddHandler(Class<T> aggregationType, Function<T[], T> aggregator,
                StringListAttributeDefinition aggregateReferences, RuntimeCapability<?> runtimeCapability, String apiCapabilityName,
                boolean dependOnProviderRegistration) {
            super(runtimeCapability, apiCapabilityName);

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
