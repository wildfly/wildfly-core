/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.operations;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESUME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.server.Services.JBOSS_SUSPEND_CONTROLLER;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceRegistry;

/**
 * Handler that suspends server operations
 *
 * @author Stuart Douglas
 */
public class ServerResumeHandler implements OperationStepHandler {

    public static final ServerResumeHandler INSTANCE = new ServerResumeHandler();

    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(RESUME, ServerDescriptions.getResourceDescriptionResolver(RUNNING_SERVER))
            .setRuntimeOnly()
            .build();

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        // Acquire the controller lock to prevent new write ops and wait until current ones are done
        context.acquireControllerLock();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                // Even though we don't read from the service registry, we have a reference to a
                // service-fronted 'processState' so tell the controller we are modifying a service
                final ServiceRegistry registry = context.getServiceRegistry(true);
                context.completeStep(new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                        if(resultAction == OperationContext.ResultAction.KEEP) {
                            //even if the timeout is zero we still pause the server
                            //to stop new requests being accepted as it is shutting down
                            ServiceController<SuspendController> shutdownController = (ServiceController<SuspendController>) registry.getRequiredService(JBOSS_SUSPEND_CONTROLLER);
                            shutdownController.getValue().resume();
                        }
                    }
                });
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
