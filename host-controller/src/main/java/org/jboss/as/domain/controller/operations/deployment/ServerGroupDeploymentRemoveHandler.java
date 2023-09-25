/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;

import java.util.List;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.deployment.ModelContentReference;
import org.jboss.dmr.ModelNode;

/**
 * Handles removal of a deployment from the model. This can be used at either the domain deployments level or the
 * server-group deployments level
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ServerGroupDeploymentRemoveHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = REMOVE;

    private final ContentRepository contentRepository;

    public ServerGroupDeploymentRemoveHandler(ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        final PathAddress operationAddress = PathAddress.pathAddress(operation.require(OP_ADDR));
        PathAddress deploymentAddress = PathAddress.pathAddress(operationAddress.getLastElement());
        ModelNode deployment = context.readResourceFromRoot(deploymentAddress, false).getModel();
        final byte[] hash;
        if (deployment.has(CONTENT)) {
            byte[] currentHash = null;
            if (deployment.get(CONTENT).has(HASH)) {
                currentHash = deployment.get(CONTENT).get(HASH).asBytes();
            } else {
                List<ModelNode> nodes = deployment.get(CONTENT).asList();
                for (ModelNode node : nodes) {
                    if (node.has(HASH)) {
                        currentHash = node.get(HASH).asBytes();
                    }
                }
            }
            hash = currentHash;
        } else {
            hash = null;
        }
        context.removeResource(PathAddress.EMPTY_ADDRESS);
        context.completeStep(new OperationContext.ResultHandler() {
            @Override
            public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                if (resultAction != OperationContext.ResultAction.ROLLBACK) {
                    if (contentRepository != null && hash != null) {
                        PathAddress operationAddress = PathAddress.pathAddress(operation.require(OP_ADDR));
                        contentRepository.removeContent(ModelContentReference.fromModelAddress(operationAddress, hash));
                    }
                }
            }
        });
    }
}
