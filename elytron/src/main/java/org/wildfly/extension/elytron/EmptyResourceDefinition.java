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

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.extension.elytron.common.TrivialCapabilityServiceRemoveHandler;
import org.wildfly.extension.elytron.common.TrivialService;
import org.wildfly.extension.elytron.common.TrivialService.ValueSupplier;

/**
 * A {@link ResourceDefinition} for a resource with no configuration.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class EmptyResourceDefinition extends SimpleResourceDefinition {

    private EmptyResourceDefinition(String pathKey, RuntimeCapability<?> runtimeCapability, OperationStepHandler add, OperationStepHandler remove) {
        super(new Parameters(PathElement.pathElement(pathKey), ElytronExtension.getResourceDescriptionResolver(pathKey))
            .setAddHandler(add)
            .setRemoveHandler(remove)
            .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilities(runtimeCapability));
    }

    static <T> ResourceDefinition create(Class<T> valueType, String pathKey, RuntimeCapability<?> runtimeCapability, ValueSupplier<T> valueSupplier) {
        AbstractAddStepHandler add = new ResourceAddHandler<T>(valueType, runtimeCapability, valueSupplier);
        OperationStepHandler remove = new TrivialCapabilityServiceRemoveHandler(add, runtimeCapability);
        return new EmptyResourceDefinition(pathKey, runtimeCapability, add, remove);
    }

    private static class ResourceAddHandler<T> extends BaseAddHandler {

        private final Class<T> valueType;
        private final RuntimeCapability<?> runtimeCapability;
        private final ValueSupplier<T> valueSupplier;

        private ResourceAddHandler(Class<T> valueType, RuntimeCapability<?> runtimeCapability, ValueSupplier<T> valueSupplier) {
            super(runtimeCapability);
            this.valueType = valueType;
            this.runtimeCapability = runtimeCapability;
            this.valueSupplier = valueSupplier;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {

        ServiceTarget serviceTarget = context.getServiceTarget();
        RuntimeCapability<?> runtimeCapability = this.runtimeCapability.fromBaseCapability(context.getCurrentAddressValue());
        ServiceName componentName = runtimeCapability.getCapabilityServiceName(valueType);

        TrivialService<T> componentService = new TrivialService<T>(valueSupplier);

        ServiceBuilder<T> componentBuilder = serviceTarget.addService(componentName, componentService);

        commonDependencies(componentBuilder)
            .setInitialMode(Mode.LAZY)
            .install();
        }
    }

}
