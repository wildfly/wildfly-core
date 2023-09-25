/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REPLACE_DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TO_REPLACE;

import java.util.LinkedList;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
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
import org.jboss.as.server.deployment.ModelContentReference;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.common.Assert;

/**
 * Handles replacement in the runtime of one deployment by another.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ServerGroupDeploymentReplaceHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = REPLACE_DEPLOYMENT;

    private final HostFileRepository fileRepository;
    private final ContentRepository contentRepository;

    public ServerGroupDeploymentReplaceHandler(final HostFileRepository fileRepository, final ContentRepository contentRepository) {
        Assert.checkNotNullParam("fileRepository", fileRepository);
        this.fileRepository = fileRepository;
        this.contentRepository = contentRepository;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        for (AttributeDefinition def : DeploymentAttributes.SERVER_GROUP_REPLACE_DEPLOYMENT_ATTRIBUTES.values()) {
            def.validateOperation(operation);
        }
        String name = DeploymentAttributes.SERVER_GROUP_REPLACE_DEPLOYMENT_ATTRIBUTES.get(NAME).resolveModelAttribute(context, operation).asString();
        String toReplace = DeploymentAttributes.SERVER_GROUP_REPLACE_DEPLOYMENT_ATTRIBUTES.get(TO_REPLACE).resolveModelAttribute(context, operation).asString();

        if (name.equals(toReplace)) {
            throw operationFailed(DomainControllerLogger.ROOT_LOGGER.cannotUseSameValueForParameters(OPERATION_NAME, NAME, TO_REPLACE, ServerGroupDeploymentRedeployHandler.OPERATION_NAME, DeploymentFullReplaceHandler.OPERATION_NAME));
        }

        final PathElement deploymentPath = PathElement.pathElement(DEPLOYMENT, name);
        final PathElement replacePath = PathElement.pathElement(DEPLOYMENT, toReplace);
        final PathAddress referenceAddress = PathAddress.pathAddress(operation.get(OP_ADDR)).append(deploymentPath);
        Resource domainDeployment;
        try {
            // check if the domain deployment exists
            domainDeployment = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS.append(deploymentPath));
        } catch (Resource.NoSuchResourceException e) {
            throw operationFailed(DomainControllerLogger.ROOT_LOGGER.noDeploymentContentWithName(name));
        }

        final ModelNode deployment = domainDeployment.getModel();
        final List<ContentReference> locallyAddedReferences = new LinkedList<ContentReference>();
        for (ModelNode content : deployment.require(CONTENT).asList()) {
            if ((content.hasDefined(HASH))) {
                ContentReference reference = ModelContentReference.fromModelAddress(referenceAddress, content.require(HASH).asBytes());
                // Ensure the local repo has the files
                fileRepository.getDeploymentFiles(reference);
                locallyAddedReferences.add(reference);
            }
        }

        final Resource serverGroup = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        if (!serverGroup.hasChild(replacePath)) {
            throw operationFailed(DomainControllerLogger.ROOT_LOGGER.noDeploymentContentWithName(toReplace));
        }
        final Resource replaceResource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS.append(replacePath));
        //
        final boolean shouldCreateResource = !serverGroup.hasChild(deploymentPath);
        final Resource deploymentResource;
        if (shouldCreateResource) {
            final Resource resource = context.createResource(PathAddress.EMPTY_ADDRESS.append(deploymentPath));
            final ModelNode deployNode = resource.getModel();
            deployNode.set(deployment); // Get the information from the domain deployment
            deployNode.remove(CONTENT); // Prune the content information
            deployNode.get(ENABLED).set(true); // Enable
        } else {
            deploymentResource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS.append(deploymentPath));
            ModelNode enabled = deploymentResource.getModel().hasDefined(ENABLED) ? deploymentResource.getModel().get(ENABLED) : ModelNode.FALSE;
            if (enabled.getType() == ModelType.BOOLEAN && enabled.asBoolean()) {
                throw operationFailed(DomainControllerLogger.ROOT_LOGGER.deploymentAlreadyStarted(toReplace));
            }
            deploymentResource.getModel().get(ENABLED).set(true);
        }
        //
        replaceResource.getModel().get(ENABLED).set(false);
        context.completeStep(new OperationContext.ResultHandler() {
            @Override
            public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                if (resultAction == OperationContext.ResultAction.KEEP) {
                    //check that if this is a server group level op the referenced deployment overlay exists
                    if (contentRepository != null && shouldCreateResource) {
                        for(ContentReference reference : locallyAddedReferences) {
                            contentRepository.addContentReference(reference);
                        }
                    }
                }
            }
        });
    }

    private static OperationFailedException operationFailed(String msg) {
        return new OperationFailedException(msg);
    }
}
