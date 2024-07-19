/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deploymentoverlay;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.dmr.ModelNode;

/**
 * @author Stuart Douglas
 */
public class DeploymentOverlayAdd extends AbstractAddStepHandler {

    public static final DeploymentOverlayAdd INSTANCE = new DeploymentOverlayAdd();

    private DeploymentOverlayAdd() {
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {

        //check that if this is a server group level op the referenced deployment overlay exists
        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        if (address.size() > 1) {
            final String name = address.getLastElement().getValue();
            context.readResourceFromRoot(PathAddress.pathAddress(PathElement.pathElement(DEPLOYMENT_OVERLAY, name)));
        }
        super.execute(context, operation);
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return false;
    }
}
