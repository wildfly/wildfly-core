/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UUID;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_HASH;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_PATH;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.getContentItem;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.isManaged;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.ExplodedContentException;
import org.jboss.as.repository.TypedInputStream;
import org.jboss.dmr.ModelNode;

/**
 * Handler for the "read-content" operation over an exploded managed deployment.
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class ManagedDeploymentReadContentHandler implements OperationStepHandler {

    protected final ContentRepository contentRepository;

    public ManagedDeploymentReadContentHandler(final ContentRepository contentRepository) {
        assert contentRepository != null : "Null contentRepository";
        this.contentRepository = contentRepository;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        if (context.getProcessType() == ProcessType.SELF_CONTAINED) {
            throw DomainControllerLogger.ROOT_LOGGER.cannotReadContentFromSelfContainedServer();
        }
        final Resource deploymentResource = context.readResource(PathAddress.EMPTY_ADDRESS);
        ModelNode contentItemNode = getContentItem(deploymentResource);
        // Validate this op is available
        if (!isManaged(contentItemNode)) {
            throw DomainControllerLogger.ROOT_LOGGER.cannotReadContentFromUnmanagedDeployment();
        }
        final byte[] deploymentHash = CONTENT_HASH.resolveModelAttribute(context, contentItemNode).asBytes();
        final ModelNode contentPath = CONTENT_PATH.resolveModelAttribute(context, operation);
        final String path = contentPath.isDefined() ? contentPath.asString() : "";
        try {
            TypedInputStream inputStream = contentRepository.readContent(deploymentHash, path);
            String uuid = context.attachResultStream(inputStream.getContentType(), inputStream);
            context.getResult().get(UUID).set(uuid);
        } catch (ExplodedContentException ex) {
            throw new OperationFailedException(ex.getMessage());
        }
    }

}
