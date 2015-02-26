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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * Abstract superclass of handlers for a discovery option resource's remove operation.
 *
 * @author Farah Juma
 */
public abstract class AbstractDiscoveryOptionRemoveHandler extends AbstractRemoveStepHandler {

    public static final String OPERATION_NAME = REMOVE;

    protected void updateOptionsAttribute(OperationContext context, ModelNode operation, String type) {

        final PathAddress operationAddress = PathAddress.pathAddress(operation.get(OP_ADDR));
        final PathAddress discoveryOptionsAddress = operationAddress.subAddress(0, operationAddress.size() - 1);
        final ModelNode discoveryOptions = Resource.Tools.readModel(context.readResourceFromRoot(discoveryOptionsAddress));

        // Get the current list of discovery options and remove the given discovery option
        // from the list to maintain the order
        final ModelNode currentList = discoveryOptions.get(ModelDescriptionConstants.OPTIONS);
        final String name = operationAddress.getLastElement().getValue();

        final ModelNode newList = new ModelNode().setEmptyList();
        for (ModelNode e : currentList.asList()) {
            final Property prop = e.asProperty();
            final String discoveryOptionType = prop.getName();
            final String discoveryOptionName = prop.getValue().get(ModelDescriptionConstants.NAME).asString();
            if (!(discoveryOptionType.equals(type) && discoveryOptionName.equals(name))) {
                newList.add(e);
            }
        }

        final ModelNode writeOp = Util.getWriteAttributeOperation(discoveryOptionsAddress, ModelDescriptionConstants.OPTIONS, newList);
        final OperationStepHandler writeHandler = context.getRootResourceRegistration().getSubModel(discoveryOptionsAddress).getOperationHandler(PathAddress.EMPTY_ADDRESS, WRITE_ATTRIBUTE_OPERATION);
        context.addStep(writeOp, writeHandler, OperationContext.Stage.MODEL);
    }
}
