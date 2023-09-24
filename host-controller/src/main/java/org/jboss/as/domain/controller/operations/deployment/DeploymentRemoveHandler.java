/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYNC_REMOVED_FOR_READD;
import static org.jboss.as.domain.controller.logging.DomainControllerLogger.ROOT_LOGGER;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.ResultAction;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.DeploymentFileRepository;
import org.jboss.as.server.deployment.DeploymentUtils;
import org.jboss.as.server.deployment.ModelContentReference;
import org.jboss.dmr.ModelNode;

/**
 * Handles removal of a deployment from the model. This can be used at either the domain deployments level
 * or the server-group deployments level
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public abstract class DeploymentRemoveHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = REMOVE;

    /** Constructor for a slave Host Controller */
    private DeploymentRemoveHandler() {
    }

    public static DeploymentRemoveHandler createForSlave(DeploymentFileRepository fileRepository, ContentRepository contentRepository) {
        return new SlaveDeploymentRemoveHandler(fileRepository, contentRepository);
    }

    public static DeploymentRemoveHandler createForMaster(ContentRepository contentRepository) {
        return new MasterDeploymentRemoveHandler(contentRepository);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        if (!isRemovedForReadd(operation)) {
            //Only check this if this was not removed with the intent of re-addition by the SyncModelOperationHandler
            checkCanRemove(context, operation);
        }
        final PathAddress address = PathAddress.pathAddress(operation.require(OP_ADDR));
        final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        final List<byte[]> deploymentHashes = DeploymentUtils.getDeploymentHash(resource);

        context.removeResource(PathAddress.EMPTY_ADDRESS);

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

                context.completeStep(new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(ResultAction resultAction, OperationContext context, ModelNode operation) {
                        if (resultAction != ResultAction.ROLLBACK) {
                            Set<String> newHashes;
                            try {
                                newHashes = DeploymentUtils.getDeploymentHexHash(context.readResource(PathAddress.EMPTY_ADDRESS, false).getModel());
                            } catch (Resource.NoSuchResourceException ex) {
                                newHashes = Collections.emptySet();
                            }
                            removeContent(address, newHashes, deploymentHashes);
                        }
                    }
                });
            }
        }, OperationContext.Stage.RUNTIME);
    }

    protected void checkCanRemove(OperationContext context, ModelNode operation) throws OperationFailedException {
        final String deploymentName = PathAddress.pathAddress(operation.require(OP_ADDR)).getLastElement().getValue();
        final Resource root = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS);

        if(root.hasChild(PathElement.pathElement(SERVER_GROUP))) {
            final List<String> badGroups = new ArrayList<String>();
            for(final Resource.ResourceEntry entry : root.getChildren(SERVER_GROUP)) {
                if(entry.hasChild(PathElement.pathElement(DEPLOYMENT, deploymentName))) {
                    badGroups.add(entry.getName());
                }
            }

            if (!badGroups.isEmpty()) {
                throw new OperationFailedException(DomainControllerLogger.ROOT_LOGGER.cannotRemoveDeploymentInUse(deploymentName, badGroups));
            }
        }
    }

    abstract void removeContent(PathAddress address, Set<String> newHashes, List<byte[]> hashes);

    private static class MasterDeploymentRemoveHandler extends DeploymentRemoveHandler {
        final ContentRepository contentRepository;

        private MasterDeploymentRemoveHandler(ContentRepository contentRepository) {
            assert contentRepository != null : "Null contentRepository";
            this.contentRepository = contentRepository;
        }

        @Override
        void removeContent(PathAddress address, Set<String> newHashes, List<byte[]> hashes) {
            for (byte[] hash : hashes) {
                try {
                    if (contentRepository != null && (newHashes.isEmpty() || !newHashes.contains(HashUtil.bytesToHexString(hash)))) {
                        contentRepository.removeContent(ModelContentReference.fromModelAddress(address, hash));
                    } else if(contentRepository != null) {
                        ROOT_LOGGER.undeployingDeploymentHasBeenRedeployed(address.getLastElement().getValue());
                    }
                } catch (Exception e) {
                    ROOT_LOGGER.debugf(e, "Exception occurred removing %s", Arrays.asList(hash));
                }
            }
        }

    }

    private static class SlaveDeploymentRemoveHandler extends DeploymentRemoveHandler {
        final DeploymentFileRepository fileRepository;
        final ContentRepository contentRepository;

        private SlaveDeploymentRemoveHandler(final DeploymentFileRepository fileRepository, final ContentRepository contentRepository) {
            assert fileRepository != null : "Null fileRepository";
            assert contentRepository != null : "Null contentRepository";
            this.fileRepository = fileRepository;
            this.contentRepository = contentRepository;
        }

        @Override
        void removeContent(PathAddress address, Set<String> newHashes, List<byte[]> hashes) {
            for (byte[] hash : hashes) {
                try {
                    if (contentRepository.hasContent(hash)) {
                        contentRepository.removeContent(ModelContentReference.fromModelAddress(address, hash));
                    } else if (newHashes.isEmpty() || !newHashes.contains(HashUtil.bytesToHexString(hash))) {
                        fileRepository.deleteDeployment(ModelContentReference.fromModelAddress(address, hash));
                    } else {
                        ROOT_LOGGER.undeployingDeploymentHasBeenRedeployed(address.getLastElement().getValue());
                    }
                } catch (Exception e) {
                    ROOT_LOGGER.debugf(e, "Exception occurred removing %s", Arrays.asList(hash));
                }
            }
        }
    }

    private boolean isRemovedForReadd(ModelNode operation) {
        return operation.hasDefined(OPERATION_HEADERS, SYNC_REMOVED_FOR_READD) &&
                operation.get(OPERATION_HEADERS, SYNC_REMOVED_FOR_READD).asBoolean();
    }
}
