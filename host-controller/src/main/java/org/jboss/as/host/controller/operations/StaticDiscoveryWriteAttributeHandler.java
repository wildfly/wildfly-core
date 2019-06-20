/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019, Red Hat, Inc., and individual contributors
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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * This is an alias for elements in the options element, to perform a write attribute operation we need to update the parent
 * discovery-options/static-discovery=primary:write-attribute(name=port,value=9999)
 * should be treated like the following
 * /host=slave/core-service=discovery-options:write-attribute(name=options,value=[{static-discovery={name=primary,
 * protocol="${jboss.domain.master.protocol:remote+http}",host="${jboss.domain.master.address}",port=9999}}]
 *
 * @author tmiyar
 *
 */
public class StaticDiscoveryWriteAttributeHandler extends ReloadRequiredWriteAttributeHandler {

    public StaticDiscoveryWriteAttributeHandler(SimpleAttributeDefinition attribute) {
        super(attribute);
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        // HCs may connect to the in either RunningMode.NORMAL or ADMIN_ONLY,
        // so the running mode doesn't figure in whether reload is required
        return !context.isBooting();
    }

    @Override
    protected void finishModelStage(OperationContext context, ModelNode operation, String attributeName, ModelNode newValue,
            ModelNode oldValue, Resource model) throws OperationFailedException {
        super.finishModelStage(context, operation, attributeName, newValue, oldValue, model);

        final PathAddress operationAddress = context.getCurrentAddress();
        final PathAddress discoveryOptionsAddress = operationAddress.getParent();
        final ModelNode discoveryOptions = context.readResourceFromRoot(discoveryOptionsAddress, false).getModel();

        // Get the current list of discovery options and set new value for attribute
        final ModelNode currentListClone = discoveryOptions.get(ModelDescriptionConstants.OPTIONS).clone();
        final String name = context.getCurrentAddressValue();
        for (ModelNode element : currentListClone.asList()) {
            final Property prop = element.asProperty();
            final String discoveryOptionType = prop.getName();
            final String discoveryOptionName = prop.getValue().get(ModelDescriptionConstants.NAME).asString();
            if ( discoveryOptionName.equals(name) && discoveryOptionType.equals(ModelDescriptionConstants.STATIC_DISCOVERY)) {
                final ModelNode node = element.get(ModelDescriptionConstants.STATIC_DISCOVERY);
                node.get(attributeName).set(newValue);
                break;
            }
        }

        final ModelNode writeOp = Util.getWriteAttributeOperation(discoveryOptionsAddress, ModelDescriptionConstants.OPTIONS, currentListClone);
        final OperationStepHandler writeHandler = context.getRootResourceRegistration().getSubModel(discoveryOptionsAddress).getOperationHandler(PathAddress.EMPTY_ADDRESS, WRITE_ATTRIBUTE_OPERATION);
        context.addStep(writeOp, writeHandler, OperationContext.Stage.MODEL, true);
    }
}