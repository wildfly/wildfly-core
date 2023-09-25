/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.operations;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.server.controller.resources.ServerRootResourceDefinition;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ServerVersionOperations {

    public static class DefaultEmptyListAttributeHandler implements OperationStepHandler {
        public static final OperationStepHandler INSTANCE = new DefaultEmptyListAttributeHandler();
        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            String attr = operation.get(ModelDescriptionConstants.NAME).asString();
            if (attr.equals(ModelDescriptionConstants.NAMESPACES)) {
                getAttributeValueOrDefault(ServerRootResourceDefinition.NAMESPACES, context);
            } else if (attr.equals(ModelDescriptionConstants.SCHEMA_LOCATIONS)) {
                getAttributeValueOrDefault(ServerRootResourceDefinition.SCHEMA_LOCATIONS, context);
            }
        }

        private void getAttributeValueOrDefault(AttributeDefinition def, OperationContext context) throws OperationFailedException {
            //TODO fails in the validator
            //final ModelNode result = def.resolveModelAttribute(context, context.readResource(PathAddress.EMPTY_ADDRESS, false).getModel());
            final ModelNode result = context.readResource(PathAddress.EMPTY_ADDRESS, false).getModel().get(def.getName());
            context.getResult().set(result.isDefined() ? result : new ModelNode().setEmptyList());
        }
    }
}
