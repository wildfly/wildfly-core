/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.ENABLED;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;

/**
 * Handles deployment into the runtime.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ServerGroupDeploymentDeployHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = DEPLOY;

    public static final ServerGroupDeploymentDeployHandler INSTANCE = new ServerGroupDeploymentDeployHandler();

    private ServerGroupDeploymentDeployHandler() {
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) {
        final ModelNode opAddr = operation.get(OP_ADDR);
        final PathAddress address = PathAddress.pathAddress(opAddr);
        final String name = address.getLastElement().getValue();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                ServerGroupDeploymentAddHandler.validateRuntimeNames(name, context, address);
            }
        }, OperationContext.Stage.MODEL);
        context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS).getModel().get(ENABLED.getName()).set(true);
    }
}
