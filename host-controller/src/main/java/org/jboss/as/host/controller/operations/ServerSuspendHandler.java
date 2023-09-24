/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.operations;

import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.SUSPEND_TIMEOUT;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.renameTimeoutToSuspendTimeout;

import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.BlockingTimeout;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * @author Stuart Douglas
 */
public class ServerSuspendHandler implements OperationStepHandler {

    private final ServerInventory serverInventory;


    public ServerSuspendHandler(ServerInventory serverInventory) {
        this.serverInventory = serverInventory;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        if (context.getRunningMode() == RunningMode.ADMIN_ONLY) {
            throw new OperationFailedException(HostControllerLogger.ROOT_LOGGER.cannotStartServersInvalidMode(context.getRunningMode()));
        }

        renameTimeoutToSuspendTimeout(operation);
        final String serverName = context.getCurrentAddressValue();
        final int suspendTimeout = SUSPEND_TIMEOUT.resolveModelAttribute(context, operation).asInt(); //timeout in seconds, by default is 0
        final BlockingTimeout blockingTimeout = BlockingTimeout.Factory.getProxyBlockingTimeout(context);

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                // WFLY-2189 trigger a write-runtime authz check
                context.getServiceRegistry(true);
                final List<ModelNode> errorResponses  = serverInventory.suspendServers(Collections.singleton(serverName), suspendTimeout, blockingTimeout);
                if ( !errorResponses.isEmpty() ){
                    context.getFailureDescription().set(errorResponses.get(0));
                }
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
