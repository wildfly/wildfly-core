/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.server.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_RESOURCE;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.ENABLED;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.RUNTIME_NAME;
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
import org.jboss.as.server.services.security.AbstractVaultReader;
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
    private final AbstractVaultReader vaultReader;

    public DeploymentRemoveHandler(ContentRepository contentRepository, final AbstractVaultReader vaultReader) {
        this.contentRepository = contentRepository;
        this.vaultReader = vaultReader;
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
                    final ModelNode contentNode = CONTENT_RESOURCE.resolveModelAttribute(context, model);
                    context.completeStep(new OperationContext.ResultHandler() {
                        @Override
                        public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                            final String managementName = context.getCurrentAddressValue();
                            if (resultAction == OperationContext.ResultAction.ROLLBACK) {
                                if (enabled) {
                                    recoverServices(context, deployment, managementName, runtimeName, contentNode,
                                            registration, mutableRegistration, vaultReader);
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
                                Set<String> newHash;
                                try {
                                    newHash = DeploymentUtils.getDeploymentHexHash(context.readResource(PathAddress.EMPTY_ADDRESS, false).getModel());
                                } catch (Resource.NoSuchResourceException ex) {
                                    newHash = Collections.emptySet();
                                }
                                for (byte[] hash : removedHashes) {
                                    try {
                                        if(newHash.isEmpty() || !newHash.contains(HashUtil.bytesToHexString(hash))) {
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
                    });
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    private void recoverServices(OperationContext context, Resource deployment, String managementName, String runtimeName,
                                   ModelNode contentNode, ImmutableManagementResourceRegistration registration,
                                   ManagementResourceRegistration mutableRegistration, final AbstractVaultReader vaultReader) {
        final DeploymentHandlerUtil.ContentItem[] contents = getContents(contentNode);
        DeploymentHandlerUtil.doDeploy(context, runtimeName, managementName, deployment, registration, mutableRegistration, vaultReader, contents);
    }
}
