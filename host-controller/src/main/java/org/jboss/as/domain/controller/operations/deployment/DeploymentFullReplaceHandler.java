/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FULL_REPLACE_DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.domain.controller.operations.deployment.AbstractDeploymentHandler.createFailureException;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_HASH;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.ENABLED;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.ResultAction;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.HostFileRepository;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.as.server.deployment.DeploymentHandlerUtils;
import org.jboss.as.server.deployment.ModelContentReference;
import org.jboss.dmr.ModelNode;

/**
 * Handles replacement in the runtime of one deployment by another.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DeploymentFullReplaceHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = FULL_REPLACE_DEPLOYMENT;

    private final ContentRepository contentRepository;
    private final HostFileRepository fileRepository;
    private final boolean isMaster;
    private final boolean isBackup;

    /**
     * Constructor for a master and slave Host Controllers
     */
    public DeploymentFullReplaceHandler(final ContentRepository contentRepository, final HostFileRepository fileRepository, final boolean isMaster, final boolean isBackup) {
        this.contentRepository = contentRepository;
        this.fileRepository = fileRepository;
        this.isMaster = isMaster;
        this.isBackup = isBackup;
    }

    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        // Validate op. Store any corrected values back to the op before manipulating further
        ModelNode correctedOperation = operation.clone();
        for (AttributeDefinition def : DeploymentAttributes.FULL_REPLACE_DEPLOYMENT_ATTRIBUTES.values()) {
            def.validateAndSet(operation, correctedOperation);
        }

        // Pull data from the op
        final String name = DeploymentAttributes.NAME.resolveModelAttribute(context, correctedOperation).asString();
        final PathElement deploymentPath = PathElement.pathElement(DEPLOYMENT, name);
        final PathAddress address = PathAddress.pathAddress(deploymentPath);
        String runtimeName = correctedOperation.hasDefined(RUNTIME_NAME)
                ? DeploymentAttributes.RUNTIME_NAME.resolveModelAttribute(context, correctedOperation).asString() : name;
        // clone the content param, so we can modify it to our own content
        ModelNode content = correctedOperation.require(CONTENT).clone();

        // Throw a specific exception if the replaced deployment doesn't already exist
        // BES 2013/10/30 -- this is pointless; the readResourceForUpdate call will throw
        // an exception with an equally informative message if the deployment doesn't exist
//        final Resource root = context.readResource(PathAddress.EMPTY_ADDRESS);
//        boolean exists = root.hasChild(deploymentPath);
//        if (!exists) {
//            throw createFailureException(MESSAGES.noDeploymentContentWithName(name));
//        }

        final ModelNode deploymentModel = context.readResourceForUpdate(PathAddress.pathAddress(deploymentPath)).getModel();

        // Keep track of hash we are replacing so we can drop it from the content repo if all is well
        ModelNode replacedContent = deploymentModel.get(CONTENT).get(0);
        final byte[] replacedHash = replacedContent.hasDefined(CONTENT_HASH.getName())
                ? CONTENT_HASH.resolveModelAttribute(context, replacedContent).asBytes() : null;

        // Set up the new content attribute
        final byte[] newHash;
        ModelNode contentItemNode = content.require(0);
        if (contentItemNode.hasDefined(HASH)) {
            newHash = contentItemNode.require(HASH).asBytes();
            if (isMaster) {
                // We are the master DC. Validate that we actually have this content.
                if (!contentRepository.hasContent(newHash)) {
                    throw createFailureException(DomainControllerLogger.ROOT_LOGGER.noDeploymentContentWithHash(HashUtil.bytesToHexString(newHash)));
                }
            } else {
                // We are a slave controller
                // Ensure the local repo has the files
                fileRepository.getDeploymentFiles(ModelContentReference.fromModelAddress(address, newHash));
            }
        } else if (DeploymentHandlerUtils.hasValidContentAdditionParameterDefined(contentItemNode)) {
            if (!isMaster) {
                // This is a slave DC. We can't handle this operation; it should have been fixed up on the master DC
                throw createFailureException(DomainControllerLogger.ROOT_LOGGER.slaveCannotAcceptUploads());
            }
            try {
                // Store and transform operation
                newHash = DeploymentUploadUtil.storeContentAndTransformOperation(context, correctedOperation, contentRepository);
            } catch (IOException e) {
                throw createFailureException(e.toString());
            }

            // Replace the op-provided content node with one that has a hash
            contentItemNode = new ModelNode();
            contentItemNode.get(HASH).set(newHash);
            content = new ModelNode();
            content.add(contentItemNode);

        } else {
            // Unmanaged content, the user is responsible for replication
            newHash = null;
        }

        // Store state to the model
        deploymentModel.get(RUNTIME_NAME).set(runtimeName);
        deploymentModel.get(CONTENT).set(content);

        // Update server groups
        final Set<PathAddress> affectedServerGroups = new HashSet<>();
        final Resource root = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        if (root.hasChild(PathElement.pathElement(SERVER_GROUP))) {
            ModelNode enabled = correctedOperation.get(ENABLED.getName());
            for (final Resource.ResourceEntry serverGroupResource : root.getChildren(SERVER_GROUP)) {
                Resource deploymentResource = serverGroupResource.getChild(deploymentPath);
                if (deploymentResource != null) {
                    ModelNode groupDeploymentModel = deploymentResource.getModel();
                    groupDeploymentModel.get(RUNTIME_NAME).set(runtimeName);
                    if (enabled.isDefined()) {
                        groupDeploymentModel.get(ENABLED.getName()).set(enabled);
                    }
                    affectedServerGroups.add(PathAddress.pathAddress(serverGroupResource.getPathElement(), deploymentPath));
                }
            }
        }

        context.completeStep(new OperationContext.ResultHandler() {
            @Override
            public void handleResult(ResultAction resultAction, OperationContext context, ModelNode operation) {
                if (resultAction == ResultAction.KEEP) {
                    if (replacedHash != null  && (newHash == null || !Arrays.equals(replacedHash, newHash))) {
                        // The old content is no longer used; clean from repos
                        ContentReference reference = ModelContentReference.fromModelAddress(address, replacedHash);
                        cleanRepositoryRef(reference);
                        for (PathAddress serverGroupAddress : affectedServerGroups) {
                            reference = ModelContentReference.fromModelAddress(serverGroupAddress, replacedHash);
                            cleanRepositoryRef(reference);
                        }
                    }
                    if (newHash != null) {
                        if (isMaster || isBackup) {
                            contentRepository.addContentReference(ModelContentReference.fromModelAddress(address, newHash));
                        }
                        for (PathAddress serverGroupAddress : affectedServerGroups) {
                            contentRepository.addContentReference(ModelContentReference.fromModelAddress(serverGroupAddress, newHash));
                        }
                    }
                } else if (newHash != null && (replacedHash == null || !Arrays.equals(replacedHash, newHash))) {
                    // Due to rollback, the new content isn't used; clean from repos
                    ContentReference reference = ModelContentReference.fromModelAddress(address, newHash);
                    cleanRepositoryRef(reference);
                    for (PathAddress serverGroupAddress : affectedServerGroups) {
                        reference = ModelContentReference.fromModelAddress(serverGroupAddress, newHash);
                        cleanRepositoryRef(reference);
                    }
                }
            }
        });
    }

    private void cleanRepositoryRef(ContentReference reference){
        if (isMaster || isBackup) {
            contentRepository.removeContent(reference);
        } else {
            fileRepository.deleteDeployment(reference);
        }
    }
}
