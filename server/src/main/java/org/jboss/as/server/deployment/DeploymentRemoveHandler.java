/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_RESOURCE_ALL;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.ENABLED;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.RUNTIME_NAME;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.addFlushHandler;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.getContents;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;

/**
 * Handles removal of a deployment from the model.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DeploymentRemoveHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = REMOVE;

    private final ContentRepository contentRepository;

    public DeploymentRemoveHandler(ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final String name = PathAddress.pathAddress(operation.require(OP_ADDR)).getLastElement().getValue();
        Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        final List<byte[]> removedHashes = DeploymentUtils.getDeploymentHash(resource);

        final Resource deployment = context.removeResource(PathAddress.EMPTY_ADDRESS);
        final ImmutableManagementResourceRegistration registration = context.getResourceRegistration();
        final ManagementResourceRegistration mutableRegistration = context.getResourceRegistrationForUpdate();
        final ModelNode model = deployment.getModel();

        if (context.isNormalServer()) {
            context.addStep(new OperationStepHandler() {
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    final String runtimeName;
                    final boolean enabled = ENABLED.resolveModelAttribute(context, model).asBoolean();
                    if (enabled) {
                        runtimeName = RUNTIME_NAME.resolveModelAttribute(context, model).asString();
                        final ServiceName deploymentUnitServiceName = Services.deploymentUnitName(runtimeName);
                        context.removeService(deploymentUnitServiceName);
                        context.removeService(deploymentUnitServiceName.append("contents"));
                    } else {
                        runtimeName = null;
                    }
                    final ModelNode contentNode = CONTENT_RESOURCE_ALL.resolveModelAttribute(context, model);
                    addFlushHandler(context, contentRepository, new OperationContext.ResultHandler() {
                        @Override
                        public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                            final String managementName = context.getCurrentAddressValue();
                            if (resultAction == OperationContext.ResultAction.ROLLBACK) {
                                if (enabled) {
                                    recoverServices(context, deployment, managementName, runtimeName, contentNode,
                                            registration, mutableRegistration);
                                }

                                if (enabled && context.hasFailureDescription()) {
                                    ServerLogger.ROOT_LOGGER.undeploymentRolledBack(runtimeName, context.getFailureDescription().asString());
                                } else if (enabled) {
                                    ServerLogger.ROOT_LOGGER.undeploymentRolledBackWithNoMessage(runtimeName);
                                }
                            } else {
                                if (enabled) {
                                    ServerLogger.ROOT_LOGGER.deploymentUndeployed(managementName, runtimeName);
                                }
                                removeContent(context, removedHashes, name);
                            }
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);
        } else {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    context.completeStep(new OperationContext.ResultHandler() {
                        @Override
                        public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                            if (resultAction != OperationContext.ResultAction.ROLLBACK) {
                                removeContent(context, removedHashes, name);
                            }
                        }
                    });

                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    private void recoverServices(OperationContext context, Resource deployment, String managementName, String runtimeName,
                                   ModelNode contentNode, ImmutableManagementResourceRegistration registration,
                                   ManagementResourceRegistration mutableRegistration) {
        final DeploymentHandlerUtil.ContentItem[] contents = getContents(contentNode);
        DeploymentHandlerUtil.doDeploy(context, runtimeName, managementName, deployment, registration, mutableRegistration, contents);
    }

    private void removeContent(OperationContext context, List<byte[]> removedHashes, String name) {
        Set<String> newHash;
        try {
            newHash = DeploymentUtils.getDeploymentHexHash(context.readResource(PathAddress.EMPTY_ADDRESS, false).getModel());
        } catch (Resource.NoSuchResourceException ex) {
            newHash = Collections.emptySet();
        }
        for (byte[] hash : removedHashes) {
            try {
                if (newHash.isEmpty() || !newHash.contains(HashUtil.bytesToHexString(hash))) {
                    contentRepository.removeContent(ModelContentReference.fromDeploymentName(name, hash));
                } else {
                    ServerLogger.ROOT_LOGGER.undeployingDeploymentHasBeenRedeployed(name);
                }
            } catch (Exception e) {
                //TODO
                ServerLogger.ROOT_LOGGER.failedToRemoveDeploymentContent(e, HashUtil.bytesToHexString(hash));
            }
        }
    }
}
