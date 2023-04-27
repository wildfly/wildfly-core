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
import static org.wildfly.extension.elytron.ClassLoadingAttributeDefinitions.resolveClassLoader;
import static org.wildfly.extension.elytron.ElytronDefinition.commonRequirements;
import static org.wildfly.extension.elytron.SecurityActions.doPrivileged;
import static org.wildfly.extension.elytron._private.ElytronCommonMessages.ROOT_LOGGER;

import java.lang.reflect.Method;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
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
class CustomComponentDefinition<C, T> extends SimpleResourceDefinition {

    static final SimpleAttributeDefinition MODULE = new SimpleAttributeDefinitionBuilder(ClassLoadingAttributeDefinitions.MODULE)
        .setRequired(true)
        .setRestartAllServices()
        .build();

    static final PropertiesAttributeDefinition CONFIGURATION = new PropertiesAttributeDefinition.Builder(ElytronCommonConstants.CONFIGURATION, true)
        .setAllowExpression(true)
        .setRestartAllServices()
        .build();

    static final AttributeDefinition[] ATTRIBUTES = {MODULE, CLASS_NAME, CONFIGURATION};

    CustomComponentDefinition(Class<C> serviceType, Function<C, T> wrapper, String pathKey, @SuppressWarnings("rawtypes") RuntimeCapability ... runtimeCapabilities) {
        super(addAddRemoveHandlers(new Parameters(PathElement.pathElement(pathKey), ElytronExtension.getResourceDescriptionResolver(pathKey))
            .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
            .setCapabilities(runtimeCapabilities), serviceType, wrapper, runtimeCapabilities));
    }

    private static <C, T> Parameters addAddRemoveHandlers(Parameters parameters, Class<C> serviceType, Function<C, T> wrapper, RuntimeCapability<?> ... runtimeCapabilities) {
        AbstractAddStepHandler add = new ComponentAddHandler<>(serviceType, wrapper, runtimeCapabilities);
        OperationStepHandler remove = new TrivialCapabilityServiceRemoveHandler(add, runtimeCapabilities);

        parameters.setAddHandler(add);
        parameters.setRemoveHandler(remove);

        return parameters;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        AbstractWriteAttributeHandler writeHandler = new ElytronReloadRequiredWriteAttributeHandler(ATTRIBUTES);
        for (AttributeDefinition current : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, writeHandler);
        }
    }

    private static class ComponentAddHandler<C, T> extends ElytronCommonBaseAddHandler {

        private final RuntimeCapability<?>[] runtimeCapabilities;
        private final Class<C> serviceType;
        private final Function<C, T> wrapper;

        private ComponentAddHandler(Class<C> serviceType, Function<C, T> wrapper, RuntimeCapability<?> ... runtimeCapabilities) {
            super(new HashSet<>(Arrays.asList(runtimeCapabilities)), ATTRIBUTES);
            this.runtimeCapabilities = runtimeCapabilities;
            this.serviceType = serviceType;
            this.wrapper = wrapper;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            ServiceTarget serviceTarget = context.getServiceTarget();

            String address = context.getCurrentAddressValue();
            RuntimeCapability<?> primaryCapability = runtimeCapabilities[0];
            ServiceName primaryServiceName = toServiceName(primaryCapability, address);

            final String module = MODULE.resolveModelAttribute(context, model).asStringOrNull();
            final String className = CLASS_NAME.resolveModelAttribute(context, model).asString();

            final Map<String, String> configurationMap;
            configurationMap = CONFIGURATION.unwrap(context, model);

            ServiceBuilder<?> serviceBuilder = serviceTarget.addService(primaryServiceName);
            for (int i = 1; i < runtimeCapabilities.length; i++) {
                serviceBuilder.addAliases(toServiceName(runtimeCapabilities[i], address));
            }

            commonRequirements(serviceBuilder)
                .setInstance(new TrivialService<>(() -> createValue(module, className, configurationMap)))
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

                Class<? extends C> typeClazz = classLoader.loadClass(className).asSubclass(serviceType);

                C component = typeClazz.getDeclaredConstructor().newInstance();

                if (configuration != null && !configuration.isEmpty()) {
                    try {
                        Method method = component.getClass().getMethod("initialize", Map.class);
                        method.invoke(component, configuration);
                    } catch (NoSuchMethodException e) {
                        throw ROOT_LOGGER.componentNotConfigurable(component.getClass().getName(), e);
                    }
                }

                return wrapper.apply(component);
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

}
