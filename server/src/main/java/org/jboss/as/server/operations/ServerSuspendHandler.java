/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.server.Services.JBOSS_SUSPEND_CONTROLLER;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.SUSPEND_TIMEOUT;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.TIMEOUT;
import static org.jboss.as.server.controller.resources.ServerRootResourceDefinition.renameTimeoutToSuspendTimeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.as.server.suspend.OperationListener;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceRegistry;

/**
 * Handler that suspends server operations
 *
 * @author Stuart Douglas
 */
public class ServerSuspendHandler implements OperationStepHandler {

    public static final ServerSuspendHandler INSTANCE = new ServerSuspendHandler();

    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.SUSPEND,
                ServerDescriptions.getResourceDescriptionResolver(RUNNING_SERVER))
            .setParameters(TIMEOUT, SUSPEND_TIMEOUT)
            .setRuntimeOnly()
            .build();

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(final OperationContext context, ModelNode operation) throws OperationFailedException {
        // Acquire the controller lock to prevent new write ops and wait until current ones are done
        context.acquireControllerLock();
        renameTimeoutToSuspendTimeout(operation);
        final int timeout = SUSPEND_TIMEOUT.resolveModelAttribute(context, operation).asInt();

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(final OperationContext context, ModelNode operation) throws OperationFailedException {
                final ServiceRegistry registry = context.getServiceRegistry(false);
                ServiceController<SuspendController> suspendControllerServiceController = (ServiceController<SuspendController>) registry.getRequiredService(JBOSS_SUSPEND_CONTROLLER);
                final SuspendController suspendController = suspendControllerServiceController.getValue();

                final CountDownLatch latch = new CountDownLatch(1);
                final AtomicBoolean cancelled = new AtomicBoolean();

                OperationListener operationListener = new OperationListener() {
                    @Override
                    public void suspendStarted() {

                    }

                    @Override
                    public void complete() {
                        suspendController.removeListener(this);
                        latch.countDown();
                    }

                    @Override
                    public void cancelled() {
                        suspendController.removeListener(this);
                        cancelled.set(true);
                        latch.countDown();
                    }

                    @Override
                    public void timeout() {
                        suspendController.removeListener(this);
                        latch.countDown();
                    }
                };
                suspendController.addListener(operationListener);
                suspendController.suspend(timeout > 0 ?  timeout * 1000 : timeout);
                if(timeout != 0 && suspendController.getState() != SuspendController.State.SUSPENDED) {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                if(cancelled.get()) {
                    context.setRollbackOnly();
                }
                context.completeStep(new RollbackHandler(suspendController));
            }
        }, OperationContext.Stage.RUNTIME);
        context.completeStep(OperationContext.ResultHandler.NOOP_RESULT_HANDLER);
    }

    private static final class RollbackHandler implements OperationContext.ResultHandler {

        private final SuspendController controller;

        private RollbackHandler(SuspendController controller) {
            this.controller = controller;
        }

        @Override
        public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
            if(resultAction == OperationContext.ResultAction.ROLLBACK) {
                controller.resume();
            }
        }
    }
}
