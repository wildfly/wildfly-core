/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.domain.management.notification.registrar;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NOTIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REGISTRAR;

import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.RestartParentWriteAttributeHandler;
import org.jboss.as.controller.ServiceRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * @author Kabir Khan
 */
public class NotificationRegistrarResourceDefinition extends SimpleResourceDefinition {

    public static final ServiceName SERVICE_NAME_BASE = ServiceName.JBOSS.append(NOTIFICATION, REGISTRAR);

    public static final PathElement PATH = PathElement.pathElement(REGISTRAR);

    static final String RESOLVER_KEY = REGISTRAR;

    public static final SimpleAttributeDefinition MODULE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.MODULE, ModelType.STRING)
            .setValidator(new StringLengthValidator(1))
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();


    public static final SimpleAttributeDefinition CODE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.CODE, ModelType.STRING)
            .setValidator(new StringLengthValidator(1))
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[] {MODULE, CODE};

    static final NotificationRegistrarResourceDefinition INSTANCE = new NotificationRegistrarResourceDefinition();

    private NotificationRegistrarResourceDefinition() {
        super(PATH,
                DomainManagementResolver.getResolver(NotificationRegistrarsRootResourceDefinition.RESOLVER_KEY, RESOLVER_KEY),
                NotificationRegistrarAddHandler.INSTANCE,
                NotificationRegistrarRemoveHandler.INSTANCE);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        OperationStepHandler writeAttributeHandler = new NotificationRegistrarWriteAttributeHandler(PATH.getKey(), ATTRIBUTES);
        for (AttributeDefinition attr : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attr, null, writeAttributeHandler);
        }
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(NotificationRegistrarPropertyResourceDefinition.INSTANCE);
    }

    static ServiceName getServiceName(PathAddress address) {
        return SERVICE_NAME_BASE.append(address.getLastElement().getValue());
    }

    static void installService(OperationContext context, PathAddress pathAddress, ModelNode model) throws OperationFailedException {
        final String className = CODE.resolveModelAttribute(context, model).asString();
        final ModuleIdentifier module = ModuleIdentifier.fromString(MODULE.resolveModelAttribute(context, model).asString());
        final ServiceName serviceName = getServiceName(pathAddress);
        Map<String, String> properties = new HashMap();
        NotificationRegistrarService.install(
                context.getServiceTarget(), serviceName, className, module,
                context.getProcessType(), context.getRunningMode(), properties);
    }


    static class NotificationRegistrarAddHandler extends AbstractAddStepHandler {
        static final NotificationRegistrarAddHandler INSTANCE = new NotificationRegistrarAddHandler();

        private NotificationRegistrarAddHandler() {
            super(ATTRIBUTES);
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            installService(context, context.getCurrentAddress(), model);
        }

    }

    private static class NotificationRegistrarWriteAttributeHandler extends RestartParentWriteAttributeHandler {
        private NotificationRegistrarWriteAttributeHandler(String parentKeyName, AttributeDefinition... definitions) {
            super(parentKeyName, definitions);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress parentAddress) {
            return getServiceName(parentAddress);
        }

        @Override
        protected void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel) throws OperationFailedException {
            installService(context, parentAddress, parentModel);
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }

        @Override
        protected boolean isResourceServiceRestartAllowed(OperationContext context, ServiceController<?> service) {
            return true;
        }
    }

    private static class NotificationRegistrarRemoveHandler extends ServiceRemoveStepHandler {
        private static final NotificationRegistrarRemoveHandler INSTANCE = new NotificationRegistrarRemoveHandler();

        private NotificationRegistrarRemoveHandler() {
            super(SERVICE_NAME_BASE, NotificationRegistrarAddHandler.INSTANCE);
        }

        @Override
        protected boolean isResourceServiceRestartAllowed(OperationContext context) {
            return true;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }

    }
}
