/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_ARCHIVE;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_HASH;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_RESOURCE_ALL;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.DOMAIN_ADD_ATTRIBUTES;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.EMPTY;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.RUNTIME_NAME;

import java.io.IOException;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.ResultAction;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.HostFileRepository;
import org.jboss.as.server.deployment.DeploymentHandlerUtil;
import org.jboss.as.server.deployment.DeploymentHandlerUtils;
import org.jboss.as.server.deployment.ModelContentReference;
import org.jboss.dmr.ModelNode;

/**
 * Handles addition of a deployment to the model.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DeploymentAddHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = ADD;

    private final ContentRepository contentRepository;
    private final HostFileRepository fileRepository;

    /** Constructor for a slave Host Controller */
    public DeploymentAddHandler(final HostFileRepository fileRepository, final ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
        this.fileRepository = fileRepository;
    }

    /**
     * Constructor for a master Host Controller
     *
     * @param contentRepository the master content repository. If {@code null} this handler will function as a slave handler would.
     */
    public DeploymentAddHandler(final ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
        this.fileRepository = null;
    }

    /**
     * {@inheritDoc}
     */
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final Resource resource = context.createResource(PathAddress.EMPTY_ADDRESS);
        ModelNode newModel = resource.getModel();

        // Store the rest of the attributes.
        // If we correct any of them (e.g. WFLY-3184) then pass the correction on to any slaves
        ModelNode correctedOperation = operation.clone();
        for (AttributeDefinition def : DOMAIN_ADD_ATTRIBUTES) {
            def.validateAndSet(correctedOperation, newModel);
            correctedOperation.get(def.getName()).set(newModel.get(def.getName()));
        }

        // TODO: JBAS-9020: for the moment overlays are not supported, so there is a single content item
        ModelNode contentItemNode = newModel.require(CONTENT_RESOURCE_ALL.getName()).require(0);
        final ModelNode opAddr = correctedOperation.get(OP_ADDR);
        final PathAddress address = PathAddress.pathAddress(opAddr);
        final String name = address.getLastElement().getValue();
        if (!newModel.hasDefined(RUNTIME_NAME.getName())) {
            newModel.get(RUNTIME_NAME.getName()).set(name);
        }

        byte[] hash = null;

        if (contentItemNode.hasDefined(CONTENT_HASH.getName())) {
            DeploymentHandlerUtil.isArchive(contentItemNode);
            hash = contentItemNode.require(CONTENT_HASH.getName()).asBytes();
            // If we are the master, validate that we actually have this content. If we're not the master
            // we do not need the content until it's added to a server group we care about, so we defer
            // pulling it until then
            if (fileRepository == null && contentRepository != null && !contentRepository.hasContent(hash)) {
                if (context.isBooting()) {
                    if (context.getRunningMode() == RunningMode.ADMIN_ONLY) {
                        // The deployment content is missing, which would be a fatal boot error if we were going to actually
                        // install services. In ADMIN-ONLY mode we allow it to give the admin a chance to correct the problem
                        DomainControllerLogger.ROOT_LOGGER.reportAdminOnlyMissingDeploymentContent(HashUtil.bytesToHexString(hash), name);
                    } else {
                        throw createFailureException(DomainControllerLogger.ROOT_LOGGER.noDeploymentContentWithHashAtBoot(HashUtil.bytesToHexString(hash), name));
                    }
                } else {
                    throw createFailureException(DomainControllerLogger.ROOT_LOGGER.noDeploymentContentWithHash(HashUtil.bytesToHexString(hash)));
                }
            } else if (fileRepository != null) {
                // Ensure the local repo has the files
                fileRepository.getDeploymentFiles(ModelContentReference.fromModelAddress(address, hash));
            }
        } else if (contentItemNode.hasDefined(EMPTY.getName())) {
            // Store and transform operation
            hash = DeploymentUploadUtil.storeEmptyContentAndTransformOperation(context, correctedOperation, contentRepository);
            contentItemNode.get(CONTENT_HASH.getName()).set(hash);
            contentItemNode.get(CONTENT_ARCHIVE.getName()).set(false);
            ModelNode content = new ModelNode();
            content.add(contentItemNode);
            newModel.get(CONTENT_RESOURCE_ALL.getName()).set(content);
        } else if (DeploymentHandlerUtils.hasValidContentAdditionParameterDefined(contentItemNode)) {
            if (fileRepository != null || contentRepository == null) {
                // This is a slave DC. We can't handle this operation; it should have been fixed up on the master DC
                throw createFailureException(DomainControllerLogger.ROOT_LOGGER.slaveCannotAcceptUploads());
            }
            try {
                // Store and transform operation
                hash = DeploymentUploadUtil.storeContentAndTransformOperation(context, correctedOperation, contentRepository);
            } catch (IOException e) {
                throw createFailureException(e.toString());
            }
            contentItemNode = new ModelNode();
            contentItemNode.get(CONTENT_HASH.getName()).set(hash);

            // We have altered contentItemNode from what's in the newModel, so store it back to model
            ModelNode content = new ModelNode();
            content.add(contentItemNode);
            newModel.get(CONTENT_RESOURCE_ALL.getName()).set(content);

        } // else would have failed validation

        if (contentRepository != null && hash != null) {
            final byte[] contentHash = hash;
            context.completeStep(new OperationContext.ResultHandler() {
                public void handleResult(ResultAction resultAction, OperationContext context, ModelNode operation) {
                    if (resultAction == ResultAction.KEEP) {
                        contentRepository.addContentReference(ModelContentReference.fromModelAddress(address, contentHash));
                    }
                }
            });
        }
    }

    private static OperationFailedException createFailureException(String msg) {
        return new OperationFailedException(msg);
    }
}
