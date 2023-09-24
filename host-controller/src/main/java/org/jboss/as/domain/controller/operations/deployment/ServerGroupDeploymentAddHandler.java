/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_HASH;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.ENABLED;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.RUNTIME_NAME_NILLABLE;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.SERVER_GROUP_ADD_ATTRIBUTES;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.HostFileRepository;
import org.jboss.as.server.deployment.ModelContentReference;
import org.jboss.dmr.ModelNode;

/**
 * Handles addition of a deployment to a server group.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ServerGroupDeploymentAddHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = ADD;

    private final HostFileRepository fileRepository;
    private final ContentRepository contentRepository;

    public ServerGroupDeploymentAddHandler(final HostFileRepository fileRepository, ContentRepository contentRepository) {
        this.fileRepository = fileRepository;
        this.contentRepository = contentRepository;
    }

    /**
     * {@inheritDoc}
     */
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final ModelNode opAddr = operation.get(OP_ADDR);
        final PathAddress address = PathAddress.pathAddress(opAddr);
        final String name = address.getLastElement().getValue();

        final Resource deploymentResource = context.readResourceFromRoot(PathAddress.pathAddress(PathElement.pathElement(DEPLOYMENT, name)));
        ModelNode deployment = deploymentResource.getModel();

        for (ModelNode content : deployment.require(CONTENT).asList()) {
            if ((content.hasDefined(CONTENT_HASH.getName()))) {
                CONTENT_HASH.validateOperation(content);
                // Ensure the local repo has the files
                ContentReference reference = ModelContentReference.fromModelAddress(address, CONTENT_HASH.resolveModelAttribute(context, content).asBytes());
                fileRepository.getDeploymentFiles(reference);
                if (contentRepository != null) {
                    contentRepository.addContentReference(reference);
                }
            }
        }

        final Resource resource = context.createResource(PathAddress.EMPTY_ADDRESS);
        final ModelNode subModel = resource.getModel();

        for (AttributeDefinition def : SERVER_GROUP_ADD_ATTRIBUTES) {
            def.validateAndSet(operation, subModel);
        }
        if (!RUNTIME_NAME_NILLABLE.resolveModelAttribute(context, subModel).isDefined()) {
            RUNTIME_NAME_NILLABLE.validateAndSet(deployment, subModel);
        }

        // Add a step to validate uniqueness of runtime names
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                validateRuntimeNames(name, context, address);
            }
        }, OperationContext.Stage.MODEL);
    }

    static void validateRuntimeNames(String deploymentName, OperationContext context, PathAddress address) throws OperationFailedException {
        ModelNode deployment = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();

        if (ENABLED.resolveModelAttribute(context, deployment).asBoolean()) {
            Resource root = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS);
            ModelNode domainDeployment = root.getChild(PathElement.pathElement(DEPLOYMENT, deploymentName)).getModel();
            String runtimeName = getRuntimeName(deploymentName, deployment, domainDeployment);
            PathAddress sgAddress = address.subAddress(0, address.size() - 1);
            Resource serverGroup = root.navigate(sgAddress);
            for (Resource.ResourceEntry re : serverGroup.getChildren(DEPLOYMENT)) {
                String reName = re.getName();
                if (!deploymentName.equals(reName)) {
                    ModelNode otherDepl = re.getModel();
                    if (ENABLED.resolveModelAttribute(context, otherDepl).asBoolean()) {
                        domainDeployment = root.getChild(PathElement.pathElement(DEPLOYMENT, reName)).getModel();
                        String otherRuntimeName = getRuntimeName(reName, otherDepl, domainDeployment);
                        if (runtimeName.equals(otherRuntimeName)) {
                            throw DomainControllerLogger.ROOT_LOGGER.runtimeNameMustBeUnique(reName, runtimeName, sgAddress.getLastElement().getValue());
                        }
                    }
                }
            }
        }
    }

    private static String getRuntimeName(String name, ModelNode deployment, ModelNode domainDeployment) {
        if (deployment.hasDefined(ModelDescriptionConstants.RUNTIME_NAME)) {
            return deployment.get(ModelDescriptionConstants.RUNTIME_NAME).asString();
        } else if (domainDeployment.hasDefined(ModelDescriptionConstants.RUNTIME_NAME)) {
            return domainDeployment.get(ModelDescriptionConstants.RUNTIME_NAME).asString();
        }
        return name;
    }
}
