/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.SUSPEND_TIMEOUT;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.TIMEOUT;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.renameTimeoutToSuspendTimeout;

import java.util.EnumSet;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
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
            public void execute(final OperationContext context, ModelNode operation) {
                AuthorizationResult authorizationResult = context.authorize(operation, EnumSet.of(Action.ActionEffect.WRITE_RUNTIME));
                if (authorizationResult.getDecision() == AuthorizationResult.Decision.DENY) {
                    throw ControllerLogger.ACCESS_LOGGER.unauthorized(operation.get(OP).asString(),
                            context.getCurrentAddress(), authorizationResult.getExplanation());
                }

                final ServerSuspendController suspendController = ServerSuspendHandler.this.suspendController;
                ServerLogger.ROOT_LOGGER.suspendingServer(seconds, TimeUnit.SECONDS);
                CompletionStage<Void> suspend = suspendController.suspend(ServerSuspendController.Context.RUNNING).whenComplete(ServerSuspendHandler::suspendComplete);
                if (seconds != 0) {
                    try {
                        if (seconds < 0) {
                            // Wait indefinitely
                            suspend.toCompletableFuture().get();
                        } else {
                            suspend.toCompletableFuture().get(seconds, TimeUnit.SECONDS);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (TimeoutException e) {
                        // Done waiting
                    } catch (ExecutionException e) {
                        context.getFailureDescription().set(e.getCause().getLocalizedMessage());
                        context.setRollbackOnly();
                    }
                }
                context.completeStep(new RollbackHandler(suspendController));
            }
        }, OperationContext.Stage.RUNTIME);
        context.completeStep(OperationContext.ResultHandler.NOOP_RESULT_HANDLER);
    }

    static void suspendComplete(Void result, Throwable exception) {
        if (exception != null) {
            ServerLogger.ROOT_LOGGER.suspendFailed(exception);
        }
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
                CompletionStage<Void> resume = this.controller.resume(ServerSuspendController.Context.RUNNING).whenComplete(ServerResumeHandler::resumeComplete);
                try {
                    resume.toCompletableFuture().get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    // Ignore - we already logged any completion failures
                }
            }
        }
    }
}
