/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.wildfly.extension.core.management;



import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODULE;
import static org.wildfly.extension.core.management.CoreManagementExtension.PROCESS_STATE_LISTENER_PATH;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.wildfly.extension.core.management.client.ProcessStateListener;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat inc.
 */
public class ProcessStateListenerResourceDefinition extends PersistentResourceDefinition {
    private static final String CLASS = "class";
    private static final String PROCESS_STATE_LISTENER_CAPABILITY_NAME = "org.wildfly.extension.core-management.process-state";
    static final RuntimeCapability<Void> PROCESS_STATE_LISTENER_CAPABILITY =
            RuntimeCapability.Builder.of(PROCESS_STATE_LISTENER_CAPABILITY_NAME, true, ProcessStateListenerService.class).build();

    public static final PropertiesAttributeDefinition PROPERTIES = new PropertiesAttributeDefinition.Builder("properties", true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();
    public static final AttributeDefinition LISTENER_CLASS = SimpleAttributeDefinitionBuilder.create(CLASS, ModelType.STRING, false)
            .setAllowExpression(false)
            .setRestartAllServices()
            .build();
    public static final AttributeDefinition LISTENER_MODULE = SimpleAttributeDefinitionBuilder.create(MODULE, ModelType.STRING, false)
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
        super(new Parameters(PROCESS_STATE_LISTENER_PATH, CoreManagementExtension.getResourceDescriptionResolver("process-state-listener"))
                .setOrderedChild()
                .setAddHandler(new ProcessStateListenerResourceDefinition.ProcessStateListenerAddHandler())
                .setRemoveHandler(new ProcessStateListenerResourceDefinition.ProcessStateListenerRemoveHandler()));
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(ATTRIBUTES);
    }

    private static class ProcessStateListenerAddHandler extends AbstractAddStepHandler  {

        ProcessStateListenerAddHandler() {
            super(PROCESS_STATE_LISTENER_CAPABILITY, ATTRIBUTES);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            super.performRuntime(context, operation, model);
            String className = LISTENER_CLASS.resolveModelAttribute(context, model).asString();
            String moduleIdentifier = LISTENER_MODULE.resolveModelAttribute(context, model).asString();
            ProcessStateListener listener = newInstance(className, moduleIdentifier);
            Map<String, String> properties = PROPERTIES.unwrap(context, model);
            int timeout = TIMEOUT.resolveModelAttribute(context, model).asInt();
            ProcessStateListenerService.install(context.getServiceTarget(),
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
            ModuleIdentifier moduleID = ModuleIdentifier.fromString(moduleIdentifier);
            final Module module;
            try {
                module = Module.getContextModuleLoader().loadModule(moduleID);
                Class<?> clazz = module.getClassLoader().loadClass(className);
                Object instance = clazz.newInstance();
                return ProcessStateListener.class.cast(instance);
            } catch (Exception e) {
                throw new OperationFailedException(e.getMessage(), e);
            }
        }
    }

    private static class ProcessStateListenerRemoveHandler  extends AbstractRemoveStepHandler {
        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            context.removeService(ProcessStateListenerService.SERVICE_NAME);
        }
    }
}
