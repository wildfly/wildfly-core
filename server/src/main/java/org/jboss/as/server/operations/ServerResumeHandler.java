/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESUME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;

import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.suspend.ServerSuspendController;
import org.jboss.dmr.ModelNode;

/**
 * Handler that resumes server operations
 *
 * @author Stuart Douglas
 */
public class ServerResumeHandler implements OperationStepHandler {

    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(RESUME, ServerDescriptions.getResourceDescriptionResolver(RUNNING_SERVER))
            .setRuntimeOnly()
            .build();

    private final ServerSuspendController suspendController;

    public ServerResumeHandler(ServerSuspendController suspendController) {
        this.suspendController = suspendController;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        // Acquire the controller lock to prevent new write ops and wait until current ones are done
        context.acquireControllerLock();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) {
                AuthorizationResult authorizationResult = context.authorize(operation, EnumSet.of(Action.ActionEffect.WRITE_RUNTIME));
                if (authorizationResult.getDecision() == AuthorizationResult.Decision.DENY) {
                    throw ControllerLogger.ACCESS_LOGGER.unauthorized(operation.get(OP).asString(),
                            context.getCurrentAddress(), authorizationResult.getExplanation());
                }
                context.completeStep(new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                        if (resultAction == OperationContext.ResultAction.KEEP) {
                            ServerLogger.ROOT_LOGGER.resumingServer();
                            CompletionStage<Void> resume = ServerResumeHandler.this.suspendController.resume(ServerSuspendController.Context.RUNNING);
                            resume.whenComplete(ServerResumeHandler::resumeComplete);
                            try {
                                resume.toCompletableFuture().get();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                resume.toCompletableFuture().cancel(false);
                            } catch (ExecutionException e) {
                                context.getFailureDescription().set(Optional.ofNullable(e.getCause()).orElse(e).toString());
                            }
                        }
                    }
                });
            }
        }, OperationContext.Stage.RUNTIME);
    }

    static void resumeComplete(Void result, Throwable exception) {
        if ((exception != null) && !(exception instanceof CancellationException)) {
            ServerLogger.ROOT_LOGGER.resumeFailed(exception);
        }
    }
}
