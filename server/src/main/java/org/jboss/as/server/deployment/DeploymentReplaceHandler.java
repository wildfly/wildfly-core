/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REPLACE_DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TO_REPLACE;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.ENABLED;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.getContents;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Handles replacement in the runtime of one deployment by another.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DeploymentReplaceHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = REPLACE_DEPLOYMENT;

    private final ContentRepository contentRepository;

    protected DeploymentReplaceHandler(ContentRepository contentRepository) {
        assert contentRepository != null : "Null contentRepository";
        this.contentRepository = contentRepository;
    }

    public static DeploymentReplaceHandler create(ContentRepository contentRepository) {
        return new DeploymentReplaceHandler(contentRepository);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        for (AttributeDefinition def : DeploymentAttributes.REPLACE_DEPLOYMENT_ATTRIBUTES.values()) {
            def.validateOperation(operation);
        }
        String name = DeploymentAttributes.REPLACE_DEPLOYMENT_ATTRIBUTES.get(NAME).resolveModelAttribute(context, operation).asString();
        String toReplace = DeploymentAttributes.REPLACE_DEPLOYMENT_ATTRIBUTES.get(TO_REPLACE).resolveModelAttribute(context, operation).asString();

        if (name.equals(toReplace)) {
            throw ServerLogger.ROOT_LOGGER.cannotReplaceDeployment(OPERATION_NAME, NAME, TO_REPLACE,
                    DeploymentRedeployHandler.OPERATION_NAME, DeploymentFullReplaceHandler.OPERATION_NAME);
        }

        final PathElement deployPath = PathElement.pathElement(DEPLOYMENT, name);
        final PathElement replacePath = PathElement.pathElement(DEPLOYMENT, toReplace);

        final Resource root = context.readResource(PathAddress.EMPTY_ADDRESS, false);
        if (! root.hasChild(replacePath)) {
            throw ServerLogger.ROOT_LOGGER.noSuchDeployment(toReplace);
        }

        final ModelNode replaceNode = context.readResourceForUpdate(PathAddress.pathAddress(replacePath)).getModel();
        final String replacedName = DeploymentAttributes.REPLACE_DEPLOYMENT_ATTRIBUTES.get(RUNTIME_NAME).resolveModelAttribute(context, replaceNode).asString();

        ModelNode deployNode;
        String runtimeName;
        if (!root.hasChild(deployPath)) {
            if (!operation.hasDefined(CONTENT)) {
                throw ServerLogger.ROOT_LOGGER.noSuchDeployment(name);
            }
            // else -- the HostController handles a server group replace-deployment like an add, so we do too

            final ModelNode content = operation.require(CONTENT);
            // TODO: JBAS-9020: for the moment overlays are not supported, so there is a single content item
            final ModelNode contentItemNode = content.require(0);
            if (contentItemNode.hasDefined(HASH)) {
                byte[] hash = contentItemNode.require(HASH).asBytes();
                addFromHash(ModelContentReference.fromDeploymentName(name, hash));
            } else {
            }
            runtimeName = operation.hasDefined(RUNTIME_NAME) ? DeploymentAttributes.REPLACE_DEPLOYMENT_ATTRIBUTES.get(RUNTIME_NAME).resolveModelAttribute(context, operation).asString() : replacedName;

            // Create the resource
            final Resource deployResource = context.createResource(PathAddress.pathAddress(deployPath));
            deployNode = deployResource.getModel();
            deployNode.get(RUNTIME_NAME).set(runtimeName);

            //TODO Assumes this can only be set by client
            deployNode.get(ModelDescriptionConstants.PERSISTENT).set(true);

            deployNode.get(CONTENT).set(content);

        } else {
            deployNode = context.readResourceForUpdate(PathAddress.pathAddress(deployPath)).getModel();
            if (ENABLED.resolveModelAttribute(context, deployNode).asBoolean()) {
                throw ServerLogger.ROOT_LOGGER.deploymentAlreadyStarted(toReplace);
            }
            runtimeName = operation.hasDefined(RUNTIME_NAME) ? DeploymentAttributes.REPLACE_DEPLOYMENT_ATTRIBUTES.get(RUNTIME_NAME).resolveModelAttribute(context, operation).asString() : deployNode.require(RUNTIME_NAME).asString();
            deployNode.get(RUNTIME_NAME).set(runtimeName);
        }

        deployNode.get(ENABLED.getName()).set(true);
        replaceNode.get(ENABLED.getName()).set(false);

        final DeploymentHandlerUtil.ContentItem[] contents = getContents(deployNode.require(CONTENT));
        DeploymentHandlerUtil.replace(context, replaceNode, runtimeName, name, replacedName, contents);
    }

    protected void addFromHash(ContentReference reference) throws OperationFailedException {
        if (!contentRepository.syncContent(reference)) {
            throw ServerLogger.ROOT_LOGGER.noSuchDeploymentContent(reference.getHexHash());
        }
    }

}
