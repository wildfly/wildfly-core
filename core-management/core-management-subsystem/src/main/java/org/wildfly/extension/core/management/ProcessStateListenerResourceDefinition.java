/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.core.management;



import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODULE;
import static org.wildfly.extension.core.management.CoreManagementExtension.PROCESS_STATE_LISTENER_PATH;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModuleIdentifierUtil;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ProcessStateNotifier;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.management.Capabilities;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.wildfly.extension.core.management.client.ProcessStateListener;
import org.wildfly.extension.core.management.logging.CoreManagementLogger;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat inc.
 */
public class ProcessStateListenerResourceDefinition extends PersistentResourceDefinition {
    private static final String CLASS = "class";
    private static final String PROCESS_STATE_LISTENER_CAPABILITY_NAME = "org.wildfly.extension.core-management.process-state";
    static final RuntimeCapability<Void> PROCESS_STATE_LISTENER_CAPABILITY =
            RuntimeCapability.Builder.of(PROCESS_STATE_LISTENER_CAPABILITY_NAME, true, Void.class)
                    .addRequirements(Capabilities.MANAGEMENT_EXECUTOR, ProcessStateNotifier.SERVICE_DESCRIPTOR)
                    .build();

    public static final PropertiesAttributeDefinition PROPERTIES = new PropertiesAttributeDefinition.Builder("properties", true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();
    public static final AttributeDefinition LISTENER_CLASS = SimpleAttributeDefinitionBuilder.create(CLASS, ModelType.STRING, false)
            .setAllowExpression(false)
            .setRestartAllServices()
            .build();
    public static final AttributeDefinition LISTENER_MODULE = SimpleAttributeDefinitionBuilder.create(MODULE, ModelType.STRING, false)
            .setCorrector(ModuleIdentifierUtil.MODULE_NAME_CORRECTOR)
            .setAllowExpression(false)
            .setRestartAllServices()
            .build();
    public static final AttributeDefinition TIMEOUT = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.TIMEOUT, ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(30))
            .setMeasurementUnit(MeasurementUnit.SECONDS)
            .setRestartAllServices()
            .build();

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        super.registerChildren(resourceRegistration);
    }

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {
            LISTENER_CLASS, LISTENER_MODULE, PROPERTIES, TIMEOUT
    };

    ProcessStateListenerResourceDefinition() {
        super(new SimpleResourceDefinition.Parameters(PROCESS_STATE_LISTENER_PATH, CoreManagementExtension.getResourceDescriptionResolver("process-state-listener"))
                .setOrderedChild()
                .setCapabilities(PROCESS_STATE_LISTENER_CAPABILITY)
                .setAddHandler(new ProcessStateListenerResourceDefinition.ProcessStateListenerAddHandler())
                .setRemoveHandler(new ProcessStateListenerResourceDefinition.ProcessStateListenerRemoveHandler()));
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(ATTRIBUTES);
    }

    private static class ProcessStateListenerAddHandler extends AbstractAddStepHandler  {

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            super.performRuntime(context, operation, model);
            String className = LISTENER_CLASS.resolveModelAttribute(context, model).asString();
            String moduleIdentifier = LISTENER_MODULE.resolveModelAttribute(context, model).asString();
            ProcessStateListener listener = newInstance(className, moduleIdentifier);
            Map<String, String> properties = PROPERTIES.unwrap(context, model);
            int timeout = TIMEOUT.resolveModelAttribute(context, model).asInt();
            ProcessStateListenerService.install(context.getCapabilityServiceTarget(),
                    context.getProcessType(),
                    context.getRunningMode(), context.getCurrentAddress().getLastElement().getValue(),
                    listener,
                    properties,
                    timeout);
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return super.requiresRuntime(context) || context.getProcessType().isServer();
        }

        private static ProcessStateListener newInstance(String className, String moduleIdentifier) throws OperationFailedException {
            final Module module;
            try {
                module = Module.getContextModuleLoader().loadModule(moduleIdentifier);
                Class<?> clazz = module.getClassLoader().loadClass(className);
                ClassLoader currentTccl = WildFlySecurityManager.getCurrentContextClassLoaderPrivileged();
                Object instance;
                try {
                    WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(module.getClassLoader());
                    instance = clazz.getConstructor(null).newInstance(null);
                } finally {
                    WildFlySecurityManager.setCurrentContextClassLoaderPrivileged(currentTccl);
                }
                return ProcessStateListener.class.cast(instance);
            } catch (ModuleLoadException e) {
                throw CoreManagementLogger.ROOT_LOGGER.errorToLoadModule(moduleIdentifier);
            } catch (ClassNotFoundException e) {
                throw CoreManagementLogger.ROOT_LOGGER.errorToLoadModuleClass(className, moduleIdentifier);
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | IllegalArgumentException | InvocationTargetException e) {
                throw CoreManagementLogger.ROOT_LOGGER.errorToInstantiateClassInstanceFromModule(className, moduleIdentifier);
            }
        }
    }

    private static class ProcessStateListenerRemoveHandler  extends AbstractRemoveStepHandler {
        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            context.removeService(PROCESS_STATE_LISTENER_CAPABILITY.getCapabilityServiceName(context.getCurrentAddressValue()));
        }
    }
}
