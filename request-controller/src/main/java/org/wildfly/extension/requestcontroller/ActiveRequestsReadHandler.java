/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.requestcontroller;

import org.jboss.as.controller.AbstractRuntimeOnlyHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;

/**
 * Reads handler for active requests
 *
 * @author Stuart Douglas
 */
class ActiveRequestsReadHandler extends AbstractRuntimeOnlyHandler {

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return true; // in WildFly 10 this worked in admin-only and there's no particular reason it shouldn't now
    }

    @Override
    protected void executeRuntimeStep(OperationContext context, ModelNode operation) throws OperationFailedException {
        ServiceController<?> service = context.getServiceRegistry(false).getService(RequestController.SERVICE_NAME);
        if(service != null) {
            RequestController requestController = (RequestController) service.getService().getValue();
            context.getResult().set(requestController.getActiveRequestCount());
        } else {
            context.getResult().set(-1);
        }
    }
}
