/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
