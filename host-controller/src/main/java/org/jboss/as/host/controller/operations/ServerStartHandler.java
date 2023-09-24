/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.operations;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.BLOCKING;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.START_MODE;

import java.util.Locale;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Starts a server.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ServerStartHandler implements OperationStepHandler {

    private final ServerInventory serverInventory;

    /**
     * Create the ServerAddHandler
     */
    public ServerStartHandler(final ServerInventory serverInventory) {
        this.serverInventory = serverInventory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        if (context.getRunningMode() == RunningMode.ADMIN_ONLY) {
            throw new OperationFailedException(HostControllerLogger.ROOT_LOGGER.cannotStartServersInvalidMode(context.getRunningMode()));
        }

        final String serverName = context.getCurrentAddressValue();
        final boolean blocking = BLOCKING.resolveModelAttribute(context, operation).asBoolean();
        final boolean suspend = START_MODE.resolveModelAttribute(context, operation).asString().toLowerCase(Locale.ENGLISH).equals(SUSPEND);

        final ModelNode model = Resource.Tools.readModel(context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, true));
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                // WFLY-2189 trigger a write-runtime authz check
                context.getServiceRegistry(true);

                final ServerStatus origStatus = serverInventory.determineServerStatus(serverName);
                if (origStatus != ServerStatus.STARTED && origStatus != ServerStatus.STARTING) {
                    final ServerStatus status = serverInventory.startServer(serverName, model, blocking, suspend);
                    persistAutoStart(context);
                    context.getResult().set(status.toString());
                } else {
                    context.getResult().set(origStatus.toString());
                }
                context.completeStep(new OperationContext.RollbackHandler() {
                    @Override
                    public void handleRollback(OperationContext context, ModelNode operation) {
                        if (origStatus != ServerStatus.STARTED && origStatus != ServerStatus.STARTING) {
                            serverInventory.stopServer(serverName, -1);
                            persistAutoStart(context);
                        }
                    }
                });
            }
            /**
             * By reading the model we ensure that ServerConfigResource.persistAutoStart will get called.
             * @param context the current operation context.
             */
            private void persistAutoStart(OperationContext context) {
                context.readResource(PathAddress.EMPTY_ADDRESS, false).getModel();
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
