/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_HASH;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.getContentItem;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.isArchive;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.isManaged;

import java.util.Arrays;
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

/**
 * Handler for the "remove-content" operation over an exploded managed deployment.
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
public class ExplodedDeploymentRemoveContentHandler implements OperationStepHandler {

    private final ContentRepository contentRepository;
    private final HostFileRepository fileRepository;
    private final boolean backup;

    /**
     * Constructor for a slave Host Controller
     *
     * @param fileRepository
     * @param contentRepository
     */
    public ExplodedDeploymentRemoveContentHandler(final boolean backup, final HostFileRepository fileRepository, final ContentRepository contentRepository) {
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
    public ExplodedDeploymentRemoveContentHandler(final ContentRepository contentRepository) {
        assert contentRepository != null : "Null contentRepository";
        this.contentRepository = contentRepository;
        this.fileRepository = null;
        this.backup = false;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        if (context.getProcessType() == ProcessType.SELF_CONTAINED) {
            throw DomainControllerLogger.ROOT_LOGGER.cannotRemoveContentFromSelfContainedServer();
        }
        final Resource deploymentResource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        ModelNode deploymentModel = deploymentResource.getModel();
        ModelNode contentItem = getContentItem(deploymentResource);
        // Validate this op is available
        if (!isManaged(contentItem)) {
            throw DomainControllerLogger.ROOT_LOGGER.cannotRemoveContentFromUnmanagedDeployment();
        } else if (isArchive(contentItem)) {
            throw DomainControllerLogger.ROOT_LOGGER.cannotRemoveContentFromUnexplodedDeployment();
        }
        final PathAddress address = PathAddress.pathAddress(operation.require(OP_ADDR));
        final byte[] oldHash = CONTENT_HASH.resolveModelAttribute(context, contentItem).asBytes();
        final byte[] newHash;
        if (fileRepository == null && contentRepository != null) { //Master DC
            try {
                newHash = DeploymentUploadUtil.removeContentFromExplodedAndTransformOperation(context, operation, contentRepository);
            } catch (ExplodedContentException e) {
                throw new OperationFailedException(e.getMessage());
            }
        } else { //Slave HC
            newHash = DeploymentUploadUtil.synchronizeSlaveHostController(operation, address, fileRepository, contentRepository, backup, oldHash);
        }
        contentItem.get(CONTENT_HASH.getName()).set(newHash);
        if (newHash != null) {
            contentItem = new ModelNode();
            contentItem.get(HASH).set(newHash);
            contentItem.get(ARCHIVE).set(false);
            ModelNode content = new ModelNode();
            content.add(contentItem);
            deploymentModel.get(CONTENT).set(content);
            context.completeStep(new OperationContext.ResultHandler() {
                @Override
                public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                    if (resultAction == OperationContext.ResultAction.KEEP) {
                        if (contentRepository != null) { //Master DC or backup
                            if (oldHash != null && (newHash == null || !Arrays.equals(oldHash, newHash))) {
                                // The old content is no longer used; clean from repos
                                contentRepository.removeContent(ModelContentReference.fromModelAddress(address, oldHash));
                            }
                            if (newHash != null) {
                                contentRepository.addContentReference(ModelContentReference.fromModelAddress(address, newHash));
                            }
                        }
                    } else {
                        if (fileRepository != null) {  // backup DC needs to  pull the content
                            fileRepository.getDeploymentFiles(ModelContentReference.fromModelAddress(address, newHash));
                        }
                        if (contentRepository != null) {
                            if (newHash != null && (oldHash == null || !Arrays.equals(oldHash, newHash))) {
                                // Due to rollback, the new content isn't used; clean from repos
                                contentRepository.removeContent(ModelContentReference.fromModelAddress(address, newHash));
                            }
                        }
                    }
                }
            });
        }
    }
}
