/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;


import static org.jboss.as.server.Services.JBOSS_SUSPEND_CONTROLLER;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.SUSPEND_TIMEOUT;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.TIMEOUT;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.renameTimeoutToSuspendTimeout;

import java.util.EnumSet;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.server.GracefulShutdownService;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceRegistry;

/**
 * Handler that starts a graceful shutdown in domain mode.
 *
 * Note that this does not actually shut down the server, it merely starts the suspend process. The server is shut down
 * by the process controller closing the STDIN stream, which then waits for this process to be complete.
 *
 * @author Stuart Douglas
 */
public class ServerDomainProcessShutdownHandler implements OperationStepHandler {


    public static final SimpleOperationDefinition DOMAIN_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.SHUTDOWN, ServerDescriptions.getResourceDescriptionResolver())
            .setParameters(TIMEOUT, SUSPEND_TIMEOUT)
            .setPrivateEntry()
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY, OperationEntry.Flag.RUNTIME_ONLY)
            .build();

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        context.acquireControllerLock();
        renameTimeoutToSuspendTimeout(operation);
        final int timeout = SUSPEND_TIMEOUT.resolveModelAttribute(context, operation).asInt(); //in milliseconds, as this is what is passed in from the HC
        // Acquire the controller lock to prevent new write ops and wait until current ones are done
        context.acquireControllerLock();
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
//                context.getServiceRegistry(true);
                context.authorize(operation, EnumSet.of(Action.ActionEffect.WRITE_RUNTIME));
                context.completeStep(new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                        if (resultAction == OperationContext.ResultAction.KEEP) {
                            //even if the timeout is zero we still pause the server
                            //to stop new requests being accepted as it is shutting down
                            final ServiceRegistry registry = context.getServiceRegistry(false);

                            // WFCORE-765 if either of the services we use are not present, graceful shutdown
                            // is not possible, but don't fail. The services may be missing if the server
                            // is already shutting down due to receiving a SIGINT
                            final ServiceController<GracefulShutdownService> gracefulController = (ServiceController<GracefulShutdownService>) registry.getService(GracefulShutdownService.SERVICE_NAME);
                            if (gracefulController != null) {
                                final ServiceController<SuspendController> suspendControllerServiceController = (ServiceController<SuspendController>) registry.getService(JBOSS_SUSPEND_CONTROLLER);
                                if (suspendControllerServiceController != null) {
                                    gracefulController.getValue().startGracefulShutdown();
                                    suspendControllerServiceController.getValue().suspend(timeout > 0 ? timeout * 1000 : timeout);
                                }
                            }
                        }
                    }
                });
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
