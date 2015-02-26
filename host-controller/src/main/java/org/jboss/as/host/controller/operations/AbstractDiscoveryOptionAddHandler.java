/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
package org.jboss.as.host.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.PlaceholderResource;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.discovery.DiscoveryOptionResourceDefinition;
import org.jboss.as.host.controller.discovery.StaticDiscoveryResourceDefinition;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Abstract superclass of handlers for a discovery option resource's add operation.
 *
 * @author Farah Juma
 */
public abstract class AbstractDiscoveryOptionAddHandler extends AbstractAddStepHandler {

    public static final String OPERATION_NAME = ADD;

    static void updateOptionsAttribute(final OperationContext context, final ModelNode operation, final String type)
            throws OperationFailedException {
        final PathAddress operationAddress = PathAddress.pathAddress(operation.get(OP_ADDR));
        final PathAddress discoveryOptionsAddress = operationAddress.subAddress(0, operationAddress.size() - 1);
        final ModelNode discoveryOptions = Resource.Tools.readModel(context.readResourceFromRoot(discoveryOptionsAddress));

        final ModelNode element = new ModelNode();
        if(ModelDescriptionConstants.CUSTOM_DISCOVERY.equals(type)) {
            final ModelNode node = element.get(ModelDescriptionConstants.CUSTOM_DISCOVERY);
            node.get(ModelDescriptionConstants.NAME).set(operationAddress.getLastElement().getValue());
            for (final AttributeDefinition attribute : DiscoveryOptionResourceDefinition.DISCOVERY_ATTRIBUTES) {
                attribute.validateAndSet(operation, node);
            }
        } else if(ModelDescriptionConstants.STATIC_DISCOVERY.equals(type)) {
            final ModelNode node = element.get(ModelDescriptionConstants.STATIC_DISCOVERY);
            node.get(ModelDescriptionConstants.NAME).set(operationAddress.getLastElement().getValue());
            for (final AttributeDefinition attribute : StaticDiscoveryResourceDefinition.STATIC_DISCOVERY_ATTRIBUTES) {
                attribute.validateAndSet(operation, node);
            }
        } else {
            throw new OperationFailedException(HostControllerLogger.ROOT_LOGGER.invalidDiscoveryType(type));
        }

        // Get the current list of discovery options and add the new discovery option to
        // this list to maintain the order
        final ModelNode list = discoveryOptions.get(ModelDescriptionConstants.OPTIONS).clone();
        if (!list.isDefined()) {
            list.setEmptyList();
        }
        list.add(element);

        final ModelNode writeOp = Util.getWriteAttributeOperation(discoveryOptionsAddress, ModelDescriptionConstants.OPTIONS, list);
        final OperationStepHandler writeHandler = context.getRootResourceRegistration().getSubModel(discoveryOptionsAddress).getOperationHandler(PathAddress.EMPTY_ADDRESS, WRITE_ATTRIBUTE_OPERATION);
        context.addStep(writeOp, writeHandler, OperationContext.Stage.MODEL, true);
    }

    @Override
    protected Resource createResource(final OperationContext context) {
        return PlaceholderResource.INSTANCE;
    }
}
