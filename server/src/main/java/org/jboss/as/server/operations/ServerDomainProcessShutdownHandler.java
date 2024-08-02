/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;

import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.SUSPEND_TIMEOUT;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.TIMEOUT;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.renameTimeoutToSuspendTimeout;

import java.util.EnumSet;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.as.server.suspend.ServerSuspendController;
import org.jboss.dmr.ModelNode;

/**
 * Handler that starts a graceful shutdown in domain mode.
 *
 * Note that this does not actually shut down the server, it merely starts the suspend process. The server is shut down
 * by the process controller closing the STDIN stream, which then waits for this process to be complete.
 *
 * @author Stuart Douglas
 */
public class ServerDomainProcessShutdownHandler implements OperationStepHandler {
    // For use by DomainServerMain
    public static final AtomicReference<CompletionStage<Void>> SUSPEND_STAGE = new AtomicReference<>();

    public static final SimpleOperationDefinition DOMAIN_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.SHUTDOWN, ServerDescriptions.getResourceDescriptionResolver())
            .setParameters(TIMEOUT, SUSPEND_TIMEOUT)
            .setPrivateEntry()
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY, OperationEntry.Flag.RUNTIME_ONLY)
            .build();

    private final ServerSuspendController suspendController;

    public ServerDomainProcessShutdownHandler(ServerSuspendController suspendController) {
        this.suspendController = suspendController;
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        context.acquireControllerLock();
        renameTimeoutToSuspendTimeout(operation);
        final int seconds = SUSPEND_TIMEOUT.resolveModelAttribute(context, operation).asInt(); //in milliseconds, as this is what is passed in from the HC

        // Acquire the controller lock to prevent new write ops and wait until current ones are done
        context.acquireControllerLock();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                context.authorize(operation, EnumSet.of(Action.ActionEffect.WRITE_RUNTIME));
                context.completeStep(new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                        if (resultAction == OperationContext.ResultAction.KEEP) {
                            //even if the timeout is zero we still pause the server
                            //to stop new requests being accepted as it is shutting down

                            CompletionStage<Void> suspend = ServerDomainProcessShutdownHandler.this.suspendController.suspend(ServerSuspendController.Context.SHUTDOWN);
                            SUSPEND_STAGE.set((seconds >= 0) ? suspend.toCompletableFuture().orTimeout(seconds, TimeUnit.SECONDS) : suspend);
                        }
                    }
                });
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
