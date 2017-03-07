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

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceRegistry;

/**
 * A {@link ResourceDefinition} for a single security property.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class SecurityPropertyResourceDefinition extends SimpleResourceDefinition {

    static final SimpleAttributeDefinition VALUE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.VALUE, ModelType.STRING, false)
        .setAllowExpression(true)
        .build();

    SecurityPropertyResourceDefinition() {
        super(PathElement.pathElement(ElytronDescriptionConstants.SECURITY_PROPERTY),
                ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.SECURITY_PROPERTY),
                new PropertyAddHandler(), new PropertyRemoveHandler());
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(VALUE, null, new WriteAttributeHandler());
    }

    private static String getSimpleName(PathAddress resourceAddress) {
        for (int i = resourceAddress.size(); i > 0; i--) {
            PathElement element = resourceAddress.getElement(i - 1);
            if (ElytronDescriptionConstants.SECURITY_PROPERTY.equals(element.getKey())) {
                return element.getValue();
            }
        }

        // Technically this should not be possible as we are using the address of the resource.
        throw new IllegalStateException("Unable to identify name of resource.");
    }

    private static SecurityPropertyService getService(OperationContext context) {
        ServiceRegistry serviceRegistry = context.getServiceRegistry(true);

        ServiceController<?> service = serviceRegistry.getService(SecurityPropertyService.SERVICE_NAME);
        if (service != null) {
            Object serviceImplementation = service.getService();
            if (serviceImplementation != null && serviceImplementation instanceof SecurityPropertyService) {
                return (SecurityPropertyService) serviceImplementation;
            }
        }

        // Again should not be reachable.
        throw new IllegalStateException("Requires service not available or wrong type.");
    }

    private static void setProperty(OperationContext context, String name, String value) {
        getService(context).setProperty(name, value);
    }

    private static void removeProperty(OperationContext context, String name) {
        getService(context).removeProperty(name);
    }

    private static class WriteAttributeHandler extends ElytronWriteAttributeHandler<String> {

        private WriteAttributeHandler() {
            super(VALUE);
        }

        @Override
        protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                ModelNode resolvedValue, ModelNode currentValue,
                org.jboss.as.controller.AbstractWriteAttributeHandler.HandbackHolder<String> handbackHolder)
                throws OperationFailedException {
            String name = getSimpleName(context.getCurrentAddress());
            String value = currentValue.asString();

            setProperty(context, name, value);

            return false;
        }

        @Override
        protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                ModelNode valueToRestore, ModelNode valueToRevert, String handback) throws OperationFailedException {
            String name = getSimpleName(context.getCurrentAddress());
            String value = valueToRestore.asString();

            setProperty(context, name, value);
        }

    }

    private static class PropertyAddHandler extends BaseAddHandler {

        private PropertyAddHandler() {
            super(VALUE);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            String name = getSimpleName(context.getCurrentAddress());
            String value = VALUE.resolveModelAttribute(context, model).asString();

            setProperty(context, name, value);
        }

        @Override
        protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {
            String name = getSimpleName(context.getCurrentAddress());
            removeProperty(context, name);
        }

    }

    private static class PropertyRemoveHandler extends ElytronRemoveStepHandler {

        private PropertyRemoveHandler() {
            super();
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            String name = getSimpleName(context.getCurrentAddress());
            removeProperty(context, name);
        }

        @Override
        protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model)
                throws OperationFailedException {
            String name = getSimpleName(context.getCurrentAddress());
            String value = VALUE.resolveModelAttribute(context, model).asString();

            setProperty(context, name, value);
        }

    }

}
