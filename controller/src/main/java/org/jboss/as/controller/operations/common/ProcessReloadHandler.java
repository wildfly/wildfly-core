/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.common;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.UninterruptibleCountDownLatch;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.remote.EarlyResponseSendListener;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.LifecycleEvent;
import org.jboss.msc.service.LifecycleListener;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * Operation handler for process reloads of servers.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public abstract class ProcessReloadHandler<T extends RunningModeControl> implements OperationStepHandler {

    /**
     * The operation name.
     */
    protected static final String OPERATION_NAME = "reload";

    protected static final AttributeDefinition ADMIN_ONLY = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.ADMIN_ONLY, ModelType.BOOLEAN, true)
                                                                    .setDefaultValue(ModelNode.FALSE).build();

    private final T runningModeControl;
    private final ControlledProcessState processState;

    private final ServiceName rootService;

    public ProcessReloadHandler(final ServiceName rootService, final T runningModeControl,
                                final ControlledProcessState processState) {
        this.rootService = rootService;
        this.runningModeControl = runningModeControl;
        this.processState = processState;
    }

    /** {@inheritDoc} */
    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                final ReloadContext<T> reloadContext = initializeReloadContext(context, operation);
                final ServiceController<?> service = context.getServiceRegistry(true).getRequiredService(rootService);
                context.completeStep(new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                        final EarlyResponseSendListener sendListener = context.getAttachment(EarlyResponseSendListener.ATTACHMENT_KEY);
                        try {
                            if (resultAction == OperationContext.ResultAction.KEEP) {
                                final UninterruptibleCountDownLatch latch = new UninterruptibleCountDownLatch(1);
                                service.addListener(new LifecycleListener() {
                                    @Override
                                    public void handleEvent(final ServiceController<?> controller, final LifecycleEvent event) {
                                        latch.awaitUninterruptibly();
                                        if (event == LifecycleEvent.DOWN) {
                                            controller.removeListener(this);
                                            reloadContext.doReload(runningModeControl);
                                            controller.setMode(ServiceController.Mode.ACTIVE);
                                        }
                                    }
                                });
                                try {
                                    reloadContext.reloadInitiated(runningModeControl);
                                    processState.setStopping();
                                    try {
                                        // If we were interrupted during setStopping (i.e. while calling process state listeners)
                                        // we want to clear that so we don't disrupt the reload of MSC services.
                                        // Once we set STOPPING state we proceed. And we don't want this thread
                                        // to have interrupted status as that will just mess up checking for
                                        // container stability
                                        Thread.interrupted();
                                        // Now that we're in STOPPING state we can send the response to the caller
                                        if (sendListener != null) {
                                            sendListener.sendEarlyResponse(resultAction);
                                        }
                                    } finally {
                                        // If we set the process state to STOPPING, we must stop
                                        service.setMode(ServiceController.Mode.NEVER);
                                    }
                                } finally {
                                    latch.countDown();
                                }
                            }
                        } finally {
                            if (sendListener != null) {
                                // even if we called this in the try block, it's ok to call it again.
                                sendListener.sendEarlyResponse(resultAction);
                            }
                        }
                    }
                });
            }
        }, OperationContext.Stage.RUNTIME);
    }

    protected abstract ReloadContext<T> initializeReloadContext(OperationContext context, ModelNode operation) throws OperationFailedException;

    protected interface ReloadContext<T> {
        void reloadInitiated(T runningModeControl);
        void doReload(T runningModeControl);
    }
}
