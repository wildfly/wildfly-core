/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import java.util.Collection;
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
    static void recreateParentService(OperationContext context, ModelNode parentModel) throws OperationFailedException {
        ConnectorAdd.INSTANCE.launchServices(context, parentModel);
    }

    static ServiceName getParentServiceName(PathAddress parentAddress) {
        return RemotingServices.serverServiceName(parentAddress.getLastElement().getValue());
    }


    static class AddResourceConnectorRestartHandler extends RestartParentResourceAddHandler {

        AddResourceConnectorRestartHandler(String parent, Collection<AttributeDefinition> attributes) {
            super(parent, attributes);
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
        protected void recreateParentService(OperationContext context, ModelNode parentModel) throws OperationFailedException {
            ConnectorChildResource.recreateParentService(context, parentModel);
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
        protected void recreateParentService(OperationContext context, ModelNode parentModel) throws OperationFailedException {
            ConnectorChildResource.recreateParentService(context, parentModel);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress parentAddress) {
            return ConnectorChildResource.getParentServiceName(parentAddress);
        }
    }

    static class RestartConnectorWriteAttributeHandler extends RestartParentWriteAttributeHandler {

        RestartConnectorWriteAttributeHandler(String parent, Collection<AttributeDefinition> attributes) {
            super(parent, attributes);
        }

        @Override
        protected void recreateParentService(OperationContext context, ModelNode parentModel) throws OperationFailedException {
            ConnectorChildResource.recreateParentService(context, parentModel);
        }

        @Override
        protected ServiceName getParentServiceName(PathAddress parentAddress) {
            return ConnectorChildResource.getParentServiceName(parentAddress);
        }
    }
}
