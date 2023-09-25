/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REDEPLOY;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_RESOURCE_ALL;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.RUNTIME_NAME;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.redeploy;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.getContents;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;

/**
 * Handles redeployment in the runtime.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DeploymentRedeployHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = REDEPLOY;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        // Get the model for the resource to retrieve the runtime-name for
        final ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
        final String name = PathAddress.pathAddress(operation.require(OP_ADDR)).getLastElement().getValue();
        final String runtimeName = RUNTIME_NAME.resolveModelAttribute(context, model).asString();
        final DeploymentHandlerUtil.ContentItem[] contents = getContents(CONTENT_RESOURCE_ALL.resolveModelAttribute(context, model));
        redeploy(context, runtimeName, name, contents);
    }
}
