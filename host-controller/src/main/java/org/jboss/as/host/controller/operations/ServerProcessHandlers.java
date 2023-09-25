/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.operations;

import java.util.EnumSet;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.access.Action;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 */
public abstract class ServerProcessHandlers implements OperationStepHandler {

    final ServerInventory serverInventory;

    ServerProcessHandlers(ServerInventory serverInventory) {
        this.serverInventory = serverInventory;
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {

        final String serverName = context.getCurrentAddressValue();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                context.authorize(operation, EnumSet.of(Action.ActionEffect.WRITE_RUNTIME)).failIfDenied(operation);
                context.completeStep(new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                        doExecute(serverName);
                    }
                });
            }
        }, OperationContext.Stage.RUNTIME);
    }

    abstract void doExecute(String serverName);

    public static class ServerDestroyHandler extends ServerProcessHandlers {

        public ServerDestroyHandler(ServerInventory serverInventory) {
            super(serverInventory);
        }

        @Override
        void doExecute(String serverName) {
            serverInventory.destroyServer(serverName);
        }

    }

    public static class ServerKillHandler extends ServerProcessHandlers {

        public ServerKillHandler(ServerInventory serverInventory) {
            super(serverInventory);
        }

        @Override
        void doExecute(String serverName) {
            serverInventory.killServer(serverName);
        }

    }

}
