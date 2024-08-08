/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;

import java.util.Optional;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.server.suspend.ServerSuspendController;
import org.jboss.dmr.ModelNode;

/**
 * Reports the current server {@link org.jboss.as.server.suspend.ServerSuspendController.State}
 *
 * @author Stuart Douglas
 */
public class SuspendStateReadHandler implements OperationStepHandler {

    private final ServerSuspendController suspendController;

    public SuspendStateReadHandler(ServerSuspendController suspendController) {
        this.suspendController = suspendController;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        context.getResult().set(Optional.ofNullable(this.suspendController).map(ServerSuspendController::getState).orElse(ServerSuspendController.State.SUSPENDED).name());
    }
}
