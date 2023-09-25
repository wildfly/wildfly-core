/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.operations;

import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.BLOCKING;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.SUSPEND_TIMEOUT;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.renameTimeoutToSuspendTimeout;

import java.util.EnumSet;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.dmr.ModelNode;

/**
 * Stops a server.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ServerStopHandler implements OperationStepHandler {

    private final ServerInventory serverInventory;

    /**
     * Create the ServerAddHandler
     */
    public ServerStopHandler(final ServerInventory serverInventory) {
        this.serverInventory = serverInventory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        renameTimeoutToSuspendTimeout(operation);
        final String serverName = context.getCurrentAddressValue();
        final boolean blocking = BLOCKING.resolveModelAttribute(context, operation).asBoolean();
        final int timeout = SUSPEND_TIMEOUT.resolveModelAttribute(context, operation).asInt();

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                // WFLY-2741 -- DO NOT call context.getServiceRegistry(true) as that will trigger blocking for
                // service container stability and one use case for this op is to recover from a
                // messed up service container from a previous op. Instead just ask for authorization.
                // Note that we already have the exclusive lock, so we are just skipping waiting for stability.
                // If another op that is a step in a composite step with this op needs to modify the container
                // it will have to wait for container stability, so skipping this only matters for the case
                // where this step is the only runtime change.
                context.authorize(operation, EnumSet.of(Action.ActionEffect.WRITE_RUNTIME)).failIfDenied(operation);

                final ServerStatus status = serverInventory.stopServer(serverName, timeout, blocking);
                try {
                    context.readResource(PathAddress.EMPTY_ADDRESS, false); //reading the resource to persist the autostart state.
                } catch (Resource.NoSuchResourceException ex) {
                    //in case the resource no longer exists.
                }
                context.getResult().set(status.toString());
                context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
