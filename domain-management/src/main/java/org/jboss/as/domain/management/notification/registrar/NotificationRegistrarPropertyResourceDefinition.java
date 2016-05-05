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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROPERTY;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.RestartParentResourceAddHandler;
import org.jboss.as.controller.RestartParentResourceRemoveHandler;
import org.jboss.as.controller.RestartParentWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.domain.management.ModelDescriptionConstants;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * @author Kabir Khan
 */
public class NotificationRegistrarPropertyResourceDefinition extends SimpleResourceDefinition {

    private static final String PARENT_KEY_NAME = NotificationRegistrarResourceDefinition.PATH.getKey();

    public static final PathElement PATH = PathElement.pathElement(PROPERTY);

    public static final SimpleAttributeDefinition VALUE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.VALUE, ModelType.STRING, true)
            .setValidator(new StringLengthValidator(1, true, true))
            .setAllowExpression(true)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{VALUE};

    static final NotificationRegistrarPropertyResourceDefinition INSTANCE = new NotificationRegistrarPropertyResourceDefinition();

    private NotificationRegistrarPropertyResourceDefinition() {
        super(PATH,
                DomainManagementResolver.getResolver(
                        NotificationRegistrarsRootResourceDefinition.RESOLVER_KEY, NotificationRegistrarResourceDefinition.RESOLVER_KEY, PROPERTY),
                PropertyAddHandler.INSTANCE,
                PropertyRemoveHandler.INSTANCE);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition attr : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attr, null, PropertyWriteAttributeHandler.INSTANCE);
        }
    }

    private static class PropertyAddHandler extends RestartParentResourceAddHandler {
        static final PropertyAddHandler INSTANCE = new PropertyAddHandler();

        PropertyAddHandler() {
            super(PARENT_KEY_NAME);
        }

        @Override
        protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
            for (AttributeDefinition attr : ATTRIBUTES) {
                attr.validateAndSet(operation, model);
            }
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress parentAddress) {
            return NotificationRegistrarResourceDefinition.getServiceName(parentAddress);
        }

        @Override
        protected void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel) throws OperationFailedException {
            NotificationRegistrarResourceDefinition.installService(context, parentAddress, parentModel);
        }

        @Override
        protected boolean isResourceServiceRestartAllowed(OperationContext context, ServiceController<?> service) {
            return true;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }
    }

    private static class PropertyRemoveHandler extends RestartParentResourceRemoveHandler {
        static final PropertyRemoveHandler INSTANCE = new PropertyRemoveHandler();

        PropertyRemoveHandler() {
            super(PARENT_KEY_NAME);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress parentAddress) {
            return NotificationRegistrarResourceDefinition.getServiceName(parentAddress);
        }

        @Override
        protected void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel) throws OperationFailedException {
            NotificationRegistrarResourceDefinition.installService(context, parentAddress, parentModel);
        }

        @Override
        protected boolean isResourceServiceRestartAllowed(OperationContext context, ServiceController<?> service) {
            return true;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }
    }

    private static class PropertyWriteAttributeHandler extends RestartParentWriteAttributeHandler {
        static final PropertyWriteAttributeHandler INSTANCE = new PropertyWriteAttributeHandler();

        PropertyWriteAttributeHandler() {
            super(PARENT_KEY_NAME, ATTRIBUTES);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress parentAddress) {
            return NotificationRegistrarResourceDefinition.getServiceName(parentAddress);
        }

        @Override
        protected void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel) throws OperationFailedException {
            NotificationRegistrarResourceDefinition.installService(context, parentAddress, parentModel);
        }

        @Override
        protected boolean isResourceServiceRestartAllowed(OperationContext context, ServiceController<?> service) {
            return true;
        }

        @Override
        protected boolean requiresRuntime(OperationContext context) {
            return true;
        }
    }
}
