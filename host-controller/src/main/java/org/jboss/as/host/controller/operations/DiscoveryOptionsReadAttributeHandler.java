/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.ResultHandler;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 *
 * @author Alexey Loubyansky
 */
public class DiscoveryOptionsReadAttributeHandler implements OperationStepHandler {

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final ModelNode result = context.getResult().setEmptyList();

        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        final ModelNode discoveryOptions = Resource.Tools.readModel(context.readResourceFromRoot(address));

        final ModelNode list = discoveryOptions.get(ModelDescriptionConstants.OPTIONS).clone();
        if (list.isDefined()) {
            for(Property option : list.asPropertyList()) {
                String type = option.getName();
                if(type.equals(ModelDescriptionConstants.CUSTOM_DISCOVERY)) {
                    // for compatibility
                    type = ModelDescriptionConstants.DISCOVERY_OPTION;
                }
                final String name = option.getValue().get(ModelDescriptionConstants.NAME).asString();
                result.add(type, name);
            }
        }
        context.completeStep(ResultHandler.NOOP_RESULT_HANDLER);
    }
}
