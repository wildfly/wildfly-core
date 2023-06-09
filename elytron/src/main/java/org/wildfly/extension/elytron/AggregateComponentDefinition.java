/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron;

import java.util.function.Function;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.wildfly.extension.elytron.common.ElytronCommonAggregateComponentDefinition;
import org.wildfly.extension.elytron.common.ElytronReloadRequiredWriteAttributeHandler;
import org.wildfly.extension.elytron.common.TrivialCapabilityServiceRemoveHandler;

/**
 * A {@link ResourceDefinition} for components that are aggregations of the same type. This is a compatibility wrapper,
 * not required by new usages of the corresponding common class.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 *
 * @implNote This wrappers mirrors all {@code create()} methods from the superclass to avoid issues with downcasting.
 * Other implementations can use {@link ElytronCommonAggregateComponentDefinition} directly.
 */
class AggregateComponentDefinition<T> extends ElytronCommonAggregateComponentDefinition<T> {

    private AggregateComponentDefinition(Class<T> classType, String pathKey, OperationStepHandler addHandler, OperationStepHandler removeHandler,
                 ListAttributeDefinition aggregateReferences, OperationStepHandler attributeWriteHandler, RuntimeCapability<?> runtimeCapability) {
        super(ElytronExtension.class, classType, pathKey, addHandler, removeHandler, aggregateReferences, attributeWriteHandler, runtimeCapability);
    }


    static <T> AggregateComponentDefinition<T> create(Class<T> aggregationType, String componentName, String referencesName,
                                                      RuntimeCapability<?> runtimeCapability, Function<T[], T> aggregator) {
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

    static <T> AggregateComponentDefinition<T> create(Class<T> aggregationType, String componentName,
                                                      String referencesName, RuntimeCapability<?> runtimeCapability, String apiCapabilityName, Function<T[], T> aggregator, boolean dependOnProviderRegistration) {
        String capabilityName = runtimeCapability.getName();
        StringListAttributeDefinition aggregateReferences = new StringListAttributeDefinition.Builder(referencesName)
                .setMinSize(2)
                .setRequired(true)
                .setCapabilityReference(capabilityName, capabilityName)//todo this is ultra fishy
                .setRestartAllServices()
                .build();

        AbstractAddStepHandler add = new AggregateApiComponentAddHandler<T>(aggregationType, aggregator, aggregateReferences, runtimeCapability, apiCapabilityName, dependOnProviderRegistration);
        OperationStepHandler remove = new TrivialCapabilityServiceRemoveHandler(add, runtimeCapability);
        OperationStepHandler write = new ElytronReloadRequiredWriteAttributeHandler(aggregateReferences);

        return new AggregateComponentDefinition<T>(aggregationType, componentName, add, remove, aggregateReferences, write, runtimeCapability);
    }


    private static class AggregateComponentAddHandler<T> extends ElytronCommonAggregateComponentDefinition.AggregateComponentAddHandler<T> {
        protected AggregateComponentAddHandler(Class<T> aggregationType, Function<T[], T> aggregator, StringListAttributeDefinition aggregateReferences, RuntimeCapability<?> runtimeCapability, boolean dependOnProviderRegistration) {
            super(ElytronExtension.class, aggregationType, aggregator, aggregateReferences, runtimeCapability, dependOnProviderRegistration);
        }
    }

    protected static class AggregateApiComponentAddHandler<T> extends ElytronCommonAggregateComponentDefinition.AggregateApiComponentAddHandler<T> {
        protected AggregateApiComponentAddHandler(Class<T> aggregationType, Function<T[], T> aggregator, StringListAttributeDefinition aggregateReferences, RuntimeCapability<?> runtimeCapability, String apiCapabilityName, boolean dependOnProviderRegistration) {
            super(ElytronExtension.class, aggregationType, aggregator, aggregateReferences, runtimeCapability, apiCapabilityName, dependOnProviderRegistration);
        }
    }
}