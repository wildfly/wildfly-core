/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_RESOURCE_ALL;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.ENABLED;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.RUNTIME_NAME;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.getContents;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Handles deployment into the runtime.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DeploymentDeployHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = DEPLOY;

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        Resource resource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        ModelNode model = resource.getModel();
        model.get(ENABLED.getName()).set(true);

        final ModelNode opAddr = operation.get(OP_ADDR);
        PathAddress address = PathAddress.pathAddress(opAddr);
        final String name = address.getLastElement().getValue();
        final String runtimeName = RUNTIME_NAME.resolveModelAttribute(context, model).asString();
        final DeploymentHandlerUtil.ContentItem[] contents = getContents(CONTENT_RESOURCE_ALL.resolveModelAttribute(context, model));
        DeploymentHandlerUtil.deploy(context, operation, runtimeName, name, contents);
        DeploymentUtils.enableAttribute(model);
    }
}
