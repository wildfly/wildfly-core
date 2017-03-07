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

import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.CLASS_NAME;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.MODULE;
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.resolveClassLoader;
import static org.wildfly.extension.elytron.ElytronDefinition.commonDependencies;
import static org.wildfly.extension.elytron.ElytronExtension.asStringIfDefined;
import static org.wildfly.extension.elytron.SecurityActions.doPrivileged;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleMapAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartException;


/**
 * A {@link SimpleResourceDefinition} for a custom configurable component.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class CustomComponentDefinition<T> extends SimpleResourceDefinition {

    static final SimpleMapAttributeDefinition CONFIGURATION = new SimpleMapAttributeDefinition.Builder(ElytronDescriptionConstants.CONFIGURATION, ModelType.STRING, true)
        .setAllowExpression(true)
        .build();

    private final Class<T> serviceType;
    private final RuntimeCapability<?>[] runtimeCapabilities;
    private final String pathKey;

    static final AttributeDefinition[] ATTRIBUTES = {MODULE, CLASS_NAME, CONFIGURATION};

    CustomComponentDefinition(Class<T> serviceType, String pathKey, @SuppressWarnings("rawtypes") RuntimeCapability ... runtimeCapabilities) {
        super(addAddRemoveHandlers(new Parameters(PathElement.pathElement(pathKey), ElytronExtension.getResourceDescriptionResolver(pathKey))
            .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilities(runtimeCapabilities), serviceType, runtimeCapabilities));

        this.serviceType = serviceType;
        this.runtimeCapabilities = runtimeCapabilities;
        this.pathKey = pathKey;
    }

    private static <T> Parameters addAddRemoveHandlers(Parameters parameters, Class<T> serviceType, RuntimeCapability<?> ... runtimeCapabilities) {
        AbstractAddStepHandler add = new ComponentAddHandler<T>(serviceType, runtimeCapabilities);
        OperationStepHandler remove = new TrivialCapabilityServiceRemoveHandler(add, runtimeCapabilities);

        parameters.setAddHandler(add);
        parameters.setRemoveHandler(remove);

        return parameters;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        WriteAttributeHandler<T> writeHandler = new WriteAttributeHandler<T>(serviceType, runtimeCapabilities[0], pathKey);
        for (AttributeDefinition current : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, writeHandler);
        }
    }

    private static class ComponentAddHandler<T> extends BaseAddHandler {

        private final RuntimeCapability<?>[] runtimeCapabilities;
        private final Class<T> serviceType;

        private ComponentAddHandler(Class<T> serviceType, RuntimeCapability<?> ... runtimeCapabilities) {
            super( new HashSet<RuntimeCapability>(Arrays.asList(runtimeCapabilities)), ATTRIBUTES);
            this.runtimeCapabilities = runtimeCapabilities;
            this.serviceType = serviceType;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();

            String address = context.getCurrentAddressValue();
            RuntimeCapability<?> primaryCapability = runtimeCapabilities[0];
            ServiceName primaryServiceName = toServiceName(primaryCapability, address);

            final String module = asStringIfDefined(context, MODULE, model);
            final String className = CLASS_NAME.resolveModelAttribute(context, model).asString();

            final Map<String, String> configurationMap;
            ModelNode configuration = CONFIGURATION.resolveModelAttribute(context, model);
            if (configuration.isDefined()) {
                configurationMap = new HashMap<String, String>();
                configuration.keys().forEach((String s) -> configurationMap.put(s, configuration.require(s).asString()));
            } else {
                configurationMap = null;
            }

            TrivialService<T> customComponentService = new TrivialService<T>(() -> createValue(module, className, configurationMap));

            ServiceBuilder<T> serviceBuilder = serviceTarget.addService(primaryServiceName, customComponentService);
            for (int i=1;i<runtimeCapabilities.length;i++) {
                serviceBuilder.addAliases(toServiceName(runtimeCapabilities[i], address));
            }

            commonDependencies(serviceBuilder)
                .setInitialMode(Mode.ACTIVE)
                .install();
        }

        private ServiceName toServiceName(RuntimeCapability<?> runtimeCapability, String addressValue) {
            return runtimeCapability.fromBaseCapability(addressValue).getCapabilityServiceName();
        }

        private T createValue(String module, String className, Map<String, String> configuration) throws StartException {
            final ClassLoader classLoader;
            try {
                classLoader = doPrivileged((PrivilegedExceptionAction<ClassLoader>) () -> resolveClassLoader(module));

                Class<? extends T> typeClazz = classLoader.loadClass(className).asSubclass(serviceType);

                T component = typeClazz.newInstance();

                if (configuration != null) {
                    if (component instanceof Configurable == false) {
                        throw ROOT_LOGGER.componentNotConfigurable(component.getClass().getName());
                    }
                    Configurable configurableComponent = (Configurable) component;
                    configurableComponent.initialize(configuration);
                }

                return component;
            } catch (PrivilegedActionException e) {
                throw new StartException(e.getCause());
            } catch (Exception e) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }

                throw new StartException(e);
            }
        }
    }

    private static class WriteAttributeHandler<T> extends ElytronRestartParentWriteAttributeHandler {

        private final RuntimeCapability<?> runtimeCapability;
        private final Class<T> serviceType;

        WriteAttributeHandler(Class<T> serviceType, RuntimeCapability<?> runtimeCapability, String pathKey) {
            super(pathKey, ATTRIBUTES);
            this.serviceType = serviceType;
            this.runtimeCapability = runtimeCapability;
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress pathAddress) {
            return runtimeCapability.fromBaseCapability(pathAddress.getLastElement().getValue()).getCapabilityServiceName(serviceType);
        }

    }

}
