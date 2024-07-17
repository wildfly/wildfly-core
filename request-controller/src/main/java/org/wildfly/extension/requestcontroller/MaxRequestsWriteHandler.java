/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.requestcontroller;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
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

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                           ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> handbackHolder) throws OperationFailedException {
        apply(context, resolvedValue);
        return false;
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                         ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {
        final ModelNode restored = context.readResource(PathAddress.EMPTY_ADDRESS).getModel().clone();
        restored.get(attributeName).set(valueToRestore);
        apply(context, valueToRestore);
    }

    private static void apply(final OperationContext context, ModelNode resolvedValue) throws OperationFailedException {
        ServiceController<?> serviceController = context.getServiceRegistry(false).getService(RequestController.SERVICE_NAME);
        if(serviceController == null) {
            return;
        }
        RequestController requestController = (RequestController) serviceController.getService().getValue();
        requestController.setMaxRequestCount(resolvedValue.asInt(-1));
    }

}
