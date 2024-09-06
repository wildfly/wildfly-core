/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.SUSPEND_TIMEOUT;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.TIMEOUT;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.renameTimeoutToSuspendTimeout;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.suspend.ServerSuspendController;
import org.jboss.dmr.ModelNode;

/**
 * Handler that suspends server operations
 *
 * @author Stuart Douglas
 */
public class ServerSuspendHandler implements OperationStepHandler {

    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.SUSPEND,
                ServerDescriptions.getResourceDescriptionResolver(RUNNING_SERVER))
            .setParameters(TIMEOUT, SUSPEND_TIMEOUT)
            .setRuntimeOnly()
            .build();

    private final ServerSuspendController suspendController;

    public ServerSuspendHandler(ServerSuspendController suspendController) {
        this.suspendController = suspendController;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(final OperationContext context, ModelNode operation) throws OperationFailedException {
        // Acquire the controller lock to prevent new write ops and wait until current ones are done
        context.acquireControllerLock();
        renameTimeoutToSuspendTimeout(operation);
        int seconds = SUSPEND_TIMEOUT.resolveModelAttribute(context, operation).asInt();

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(final OperationContext context, ModelNode operation) throws OperationFailedException {
                final ServerSuspendController suspendController = ServerSuspendHandler.this.suspendController;
                ServerLogger.ROOT_LOGGER.suspendingServer(seconds, TimeUnit.SECONDS);
                CompletableFuture<Void> suspend = suspendController.suspend(ServerSuspendController.Context.RUNNING).toCompletableFuture();
                if (seconds >= 0) {
                    suspend.completeOnTimeout(null, seconds, TimeUnit.SECONDS);
                }
                try {
                    suspend.join();
                } catch (CancellationException e) {
                    context.setRollbackOnly();
                }
                context.completeStep(new RollbackHandler(suspendController));
            }
        }, OperationContext.Stage.RUNTIME);
        context.completeStep(OperationContext.ResultHandler.NOOP_RESULT_HANDLER);
    }

    private static final class RollbackHandler implements OperationContext.ResultHandler {

        private final ServerSuspendController controller;

        private RollbackHandler(ServerSuspendController controller) {
            this.controller = controller;
        }

        @Override
        public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
            if (resultAction == OperationContext.ResultAction.ROLLBACK) {
                ServerLogger.ROOT_LOGGER.resumingServer();
                this.controller.resume(ServerSuspendController.Context.RUNNING).toCompletableFuture().join();
            }
        }
    }
}
