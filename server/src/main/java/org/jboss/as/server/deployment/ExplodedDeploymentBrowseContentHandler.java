/*
 * Copyright 2016 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.server.deployment;

import static org.jboss.as.server.controller.resources.DeploymentAttributes.ARCHIVE;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_HASH;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.DEPLOYMENT_CONTENT_PATH;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.DEPTH;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.getContentItem;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.isArchive;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.isManaged;

import static org.jboss.as.server.deployment.DeploymentHandlerUtils.createFailureException;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.repository.ContentFilter;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.ExplodedContentException;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Handler for the "browse-content" operation over an exploded managed deployment.
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class ExplodedDeploymentBrowseContentHandler implements OperationStepHandler {

    protected final ContentRepository contentRepository;

    public ExplodedDeploymentBrowseContentHandler(final ContentRepository contentRepository) {
        assert contentRepository != null : "Null contentRepository";
        this.contentRepository = contentRepository;
    }
    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        if (context.getProcessType() == ProcessType.SELF_CONTAINED) {
            throw ServerLogger.ROOT_LOGGER.cannotReadContentFromSelfContainedServer();
        }
        final Resource deploymentResource = context.readResource(PathAddress.EMPTY_ADDRESS);
        ModelNode contentItemNode = getContentItem(deploymentResource);
        // Validate this op is available
        if (!isManaged(contentItemNode)) {
            throw ServerLogger.ROOT_LOGGER.cannotReadContentFromUnmanagedDeployment();
        } else if (isArchive(contentItemNode)) {
            throw ServerLogger.ROOT_LOGGER.cannotReadContentFromUnexplodedDeployment();
        }
        final byte[] deploymentHash = CONTENT_HASH.resolveModelAttribute(context, contentItemNode).asBytes();
        final ModelNode pathNode = DEPLOYMENT_CONTENT_PATH.resolveModelAttribute(context, operation);
        final String path;
        if(pathNode.isDefined()) {
            path = pathNode.asString();
        } else {
            path = "";
        }
        int depth = DEPTH.resolveModelAttribute(context, operation).asInt();
        boolean explodable = ARCHIVE.resolveModelAttribute(context, operation).asBoolean();
        try {
            for (String contentPath : contentRepository.listContent(deploymentHash, path, ContentFilter.Factory.createContentFilter(depth, explodable))) {
                context.getResult().add(contentPath);
            }
        } catch (ExplodedContentException ex) {
            throw createFailureException(ex.toString());
        }
    }
}
