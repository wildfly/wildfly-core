/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors as indicated
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

package org.jboss.as.remoting;

import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.RestartParentResourceAddHandler;
import org.jboss.as.controller.RestartParentResourceRemoveHandler;
import org.jboss.as.controller.RestartParentWriteAttributeHandler;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.msc.service.ServiceName;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
abstract class ConnectorChildResource extends SimpleResourceDefinition {

    public ConnectorChildResource(PathElement pathElement, ResourceDescriptionResolver descriptionResolver, OperationStepHandler addHandler, OperationStepHandler removeHandler) {
        this(new Parameters(pathElement, descriptionResolver).setAddHandler(addHandler).setRemoveHandler(removeHandler));
    }

    public ConnectorChildResource(Parameters parameters) {
        super(parameters);
    }
    static void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel) throws OperationFailedException {
        ConnectorAdd.INSTANCE.launchServices(context, parentAddress.getLastElement().getValue(), parentModel);
    }

    static ServiceName getParentServiceName(PathAddress parentAddress) {
        return RemotingServices.serverServiceName(parentAddress.getLastElement().getValue());
    }


    static class AddResourceConnectorRestartHandler extends RestartParentResourceAddHandler {
        private final AttributeDefinition[] attributes;

        AddResourceConnectorRestartHandler(String parent, AttributeDefinition... attributes) {
            super(parent);
            this.attributes = attributes;
        }

        protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
            for (AttributeDefinition ad : attributes) {
                validateProperty(operation);
                ad.validateAndSet(operation, model);
            }
        }

        /**
         * Perform remoting specific validation.
         * @param operation
         */
        private void validateProperty(ModelNode operation) throws OperationFailedException {
            ModelNode addressModelNode = operation.get(ModelDescriptionConstants.ADDRESS);
            final ClassLoader loader = WildFlySecurityManager.getClassLoaderPrivileged(ConnectorChildResource.class);
            if (addressModelNode.isDefined()) {
                List<Property> propertyListModelNode = addressModelNode.asPropertyList();
                for (Property property : propertyListModelNode) {
                    if (property.getName().equals(CommonAttributes.PROPERTY)) {
                        ConnectorUtils.getAndValidateOption(loader, property.getValue().asString());
                    }
                }
            }
        }

        @Override
        protected void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel) throws OperationFailedException {
            ConnectorChildResource.recreateParentService(context, parentAddress, parentModel);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress parentAddress) {
            return ConnectorChildResource.getParentServiceName(parentAddress);
        }
    }

    static class RemoveResourceConnectorRestartHandler extends RestartParentResourceRemoveHandler {
        RemoveResourceConnectorRestartHandler(String parent) {
            super(parent);
        }

        @Override
        protected void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel) throws OperationFailedException {
            ConnectorChildResource.recreateParentService(context, parentAddress, parentModel);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress parentAddress) {
            return ConnectorChildResource.getParentServiceName(parentAddress);
        }
    }

    static class RestartConnectorWriteAttributeHandler extends RestartParentWriteAttributeHandler {

        RestartConnectorWriteAttributeHandler(String parent, AttributeDefinition... attributes) {
            super(parent, attributes);
        }

        @Override
        protected void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel) throws OperationFailedException {
            ConnectorChildResource.recreateParentService(context, parentAddress, parentModel);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress parentAddress) {
            return ConnectorChildResource.getParentServiceName(parentAddress);
        }
    }
}
