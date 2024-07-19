/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
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
 * protocol="${jboss.domain.primary.protocol:remote+http}",host="${jboss.domain.primary.address}",port=9999}}]
 *
 * @author tmiyar
 *
 */
public class StaticDiscoveryWriteAttributeHandler extends ReloadRequiredWriteAttributeHandler {

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