/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.requestcontroller;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;

/**
 * Write handler for the max requests attribute
 *
 * @author Stuart Douglas
 */
class MaxRequestsWriteHandler extends AbstractWriteAttributeHandler<Void> {

    private final AttributeDefinition attributeDefinition;

    MaxRequestsWriteHandler(final AttributeDefinition attributeDefinition) {
        super(attributeDefinition);
        this.attributeDefinition = attributeDefinition;
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                           ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> handbackHolder) throws OperationFailedException {
        final ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
        apply(context, model);

        return false;
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                         ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {
        final ModelNode restored = context.readResource(PathAddress.EMPTY_ADDRESS).getModel().clone();
        restored.get(attributeName).set(valueToRestore);
        apply(context, restored);
    }

    private void apply(final OperationContext context, final ModelNode model) throws OperationFailedException {
        ServiceController<?> serviceController = context.getServiceRegistry(false).getService(RequestController.SERVICE_NAME);
        if(serviceController == null) {
            return;
        }
        RequestController requestController = (RequestController) serviceController.getService().getValue();
        final ModelNode modelNode = this.attributeDefinition.resolveModelAttribute(context, model);
        if(!modelNode.isDefined()) {
            requestController.setMaxRequestCount(-1);
        } else {
            requestController.setMaxRequestCount(modelNode.asInt());
        }
    }

}
