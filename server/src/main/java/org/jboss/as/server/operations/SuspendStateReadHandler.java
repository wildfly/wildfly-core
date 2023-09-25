/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;

import static org.jboss.as.server.Services.JBOSS_SUSPEND_CONTROLLER;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;

/**
 * Reports the current server {@link org.jboss.as.server.suspend.SuspendController.State}
 *
 * @author Stuart Douglas
 */
public class SuspendStateReadHandler implements OperationStepHandler {

    public static final SuspendStateReadHandler INSTANCE = new SuspendStateReadHandler();

    private SuspendStateReadHandler(){}

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        @SuppressWarnings("unchecked")
        ServiceController<SuspendController> sc = (ServiceController<SuspendController>) context.getServiceRegistry(false).getService(JBOSS_SUSPEND_CONTROLLER);
        SuspendController.State state;
        if(sc != null) {
            state = sc.getValue().getState();
        } else {
            // Either we haven't installed the SC yet or we're stopping and it's been removed
            // If we haven't installed, when we do its initial state is SUSPENDED
            // If it's been removed, it's last state was SUSPENDED.
            // So, report that.
            state = SuspendController.State.SUSPENDED;
        }
        context.getResult().set(state.name());
    }


}
