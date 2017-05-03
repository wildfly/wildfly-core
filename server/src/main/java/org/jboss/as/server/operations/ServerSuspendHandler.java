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

package org.jboss.as.server.operations;


import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.as.server.suspend.OperationListener;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceRegistry;

/**
 * Handler that suspends server operations
 *
 * @author Stuart Douglas
 */
public class ServerSuspendHandler implements OperationStepHandler {

    protected static final String OPERATION_NAME = ModelDescriptionConstants.SUSPEND;

    public static final ServerSuspendHandler INSTANCE = new ServerSuspendHandler();

    /**
     * The timeout in seconds, the operation will block until either the timeout is reached or the server successfully suspends
     *
     * -1 : wait indefinitely
     *  0 : return immediately (default)
     * >0 : wait n seconds
     */
    protected static final SimpleAttributeDefinition TIMEOUT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.TIMEOUT, ModelType.INT)
            .setDefaultValue(new ModelNode(0))
            .setRequired(false)
            .setMeasurementUnit(MeasurementUnit.SECONDS)
            .build();

    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.SUSPEND, ServerDescriptions.getResourceDescriptionResolver())
            .setParameters(TIMEOUT)
            .setRuntimeOnly()
            .build();

    public static final SimpleOperationDefinition DOMAIN_DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.SUSPEND, ServerDescriptions.getResourceDescriptionResolver())
            .setParameters(TIMEOUT)
            .setPrivateEntry() // For now
            .withFlags(OperationEntry.Flag.HOST_CONTROLLER_ONLY, OperationEntry.Flag.RUNTIME_ONLY)
            .build();
    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(final OperationContext context, ModelNode operation) throws OperationFailedException {
        // Acquire the controller lock to prevent new write ops and wait until current ones are done
        final int timeout = TIMEOUT.resolveModelAttribute(context, operation).asInt(); //in seconds, need to convert to ms
        context.acquireControllerLock();

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(final OperationContext context, ModelNode operation) throws OperationFailedException {
                final ServiceRegistry registry = context.getServiceRegistry(false);
                ServiceController<SuspendController> suspendControllerServiceController = (ServiceController<SuspendController>) registry.getRequiredService(SuspendController.SERVICE_NAME);
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
                if(timeout != 0) {
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
