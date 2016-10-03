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
package org.jboss.as.domain.controller.operations.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_HASH;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.ENABLED;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.getContentItem;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.isArchive;
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
import org.jboss.as.repository.HostFileRepository;
import org.jboss.as.server.deployment.ModelContentReference;
import org.jboss.dmr.ModelNode;

import static org.jboss.as.server.controller.resources.DeploymentAttributes.DEPLOYMENT_CONTENT_PATH;


/**
 * Handler for the "explode" operation that can be performed against zipped managed content.
 *
 * @author Brian Stansberry
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class DeploymentExplodeHandler implements OperationStepHandler {

    private final ContentRepository contentRepository;
    private final HostFileRepository fileRepository;
    private final boolean backup;

    /**
     * Constructor for a slave Host Controller
     *
     * @param backup
     * @param fileRepository
     * @param contentRepository
     */
    public DeploymentExplodeHandler(final boolean backup, final HostFileRepository fileRepository, final ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
        this.fileRepository = fileRepository;
        this.backup = backup;
    }

    /**
     * Constructor for a master Host Controller
     *
     * @param contentRepository the master content repository. If {@code null} this handler will function as a slave
     * handler would.
     */
    public DeploymentExplodeHandler(final ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
        this.fileRepository = null;
        this.backup = false;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        if (context.getProcessType() == ProcessType.SELF_CONTAINED) {
            throw DomainControllerLogger.ROOT_LOGGER.cannotExplodeDeploymentOfSelfContainedServer();
        }
        ModelNode explodedPath = DEPLOYMENT_CONTENT_PATH.resolveModelAttribute(context, operation);
        final Resource deploymentResource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        ModelNode contentItem = getContentItem(deploymentResource);

        // Validate this op is available
        if (!isManaged(contentItem)) {
            throw DomainControllerLogger.ROOT_LOGGER.cannotExplodeUnmanagedDeployment();
        }
        if (!isArchive(contentItem) && !explodedPath.isDefined()) {
            throw DomainControllerLogger.ROOT_LOGGER.cannotExplodeAlreadyExplodedDeployment();
        }
        if (isArchive(contentItem) && explodedPath.isDefined()) {
            throw DomainControllerLogger.ROOT_LOGGER.cannotExplodeSubDeploymentOfUnexplodedDeployment();
        }
        final PathAddress address = PathAddress.pathAddress(operation.require(OP_ADDR));
        ModelNode deploymentModel = deploymentResource.getModel();
        boolean enabled = ENABLED.resolveModelAttribute(context, deploymentModel).asBoolean();
        if (enabled) {
            throw DomainControllerLogger.ROOT_LOGGER.cannotExplodeEnabledDeployment();
        }
        final byte[] oldHash = CONTENT_HASH.resolveModelAttribute(context, contentItem).asBytes();
        final byte[] newHash;
        if (fileRepository == null && contentRepository != null) { //Master DC
            try {
                newHash = DeploymentUploadUtil.explodeContentAndTransformOperation(context, operation, contentRepository);
            } catch (ExplodedContentException e) {
                throw new OperationFailedException(e);
            }
        } else { //Slave HC
            newHash = DeploymentUploadUtil.synchronizeSlaveHostController(operation, address, fileRepository, contentRepository, backup, oldHash);
        }
        if (newHash != null) {
            contentItem = new ModelNode();
            contentItem.get(HASH).set(newHash);
            contentItem.get(ARCHIVE).set(false);
            ModelNode content = new ModelNode();
            content.add(contentItem);
            deploymentModel.get(CONTENT).set(content);
            if (contentRepository != null) { //Master DC or backup
                context.completeStep(new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                        if (resultAction == OperationContext.ResultAction.KEEP) {
                            if (oldHash != null && contentRepository.hasContent(oldHash)) {
                                contentRepository.removeContent(ModelContentReference.fromModelAddress(address, oldHash));
                            }
                            if (contentRepository.hasContent(newHash)) {
                                contentRepository.addContentReference(ModelContentReference.fromModelAddress(context.getCurrentAddress(), newHash));
                            }
                        } // else the model update will be reverted and no ref content repo references changes will be made so
                        // the newly exploded content will be eligible for content repo gc
                    }
                });
            }
        }
    }
}