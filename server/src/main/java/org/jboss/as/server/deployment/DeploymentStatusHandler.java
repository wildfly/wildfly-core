/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import static org.jboss.as.server.controller.resources.DeploymentAttributes.ENABLED;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.RUNTIME_NAME;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;

/**
 * @author Jason T. Greene
 */
public class DeploymentStatusHandler implements OperationStepHandler {

    public static final OperationStepHandler INSTANCE = new DeploymentStatusHandler();
    private static final ModelNode NO_METRICS = new ModelNode("no metrics available");

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final ModelNode deployment = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
        final boolean isEnabled = ENABLED.resolveModelAttribute(context, deployment).asBoolean();
        final String runtimeName = RUNTIME_NAME.resolveModelAttribute(context, deployment).asString();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(final OperationContext context, final ModelNode operation) {
                final ModelNode result = context.getResult();
                if (!isEnabled) {
                    result.set(AbstractDeploymentUnitService.DeploymentStatus.STOPPED.toString());
                } else {
                    final ServiceController<?> controller = context.getServiceRegistry(false).getService(Services.deploymentUnitName(runtimeName));
                    if (controller != null) {
                        if (controller.getState() == ServiceController.State.DOWN && controller.missing().isEmpty()) {
                            result.set(AbstractDeploymentUnitService.DeploymentStatus.STOPPED.toString());
                        } else {
                            result.set(((AbstractDeploymentUnitService) controller.getService()).getStatus().toString());
                        }
                    } else {
                        result.set(NO_METRICS);
                    }
                }
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
