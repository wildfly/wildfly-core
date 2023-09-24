/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_ARCHIVE;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_HASH;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_PATH;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_RELATIVE_TO;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_RESOURCE_ALL;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.ENABLED;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.EMPTY;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.OWNER;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.PERSISTENT;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.RUNTIME_NAME;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.SERVER_ADD_ATTRIBUTES;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.addFlushHandler;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.asString;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.getInputStream;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.hasValidContentAdditionParameterDefined;

import java.io.IOException;
import java.io.InputStream;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.ResultAction;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;

import static org.jboss.as.server.deployment.DeploymentHandlerUtils.createFailureException;

/**
 * Handles addition of a deployment to the model.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DeploymentAddHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = ADD;

    protected final ContentRepository contentRepository;

    private final DeploymentTransformation deploymentTransformation;


    protected DeploymentAddHandler(final ContentRepository contentRepository) {
        assert contentRepository != null : "Null contentRepository";
        this.contentRepository = contentRepository;
        this.deploymentTransformation = new DeploymentTransformation();
    }

    public static DeploymentAddHandler create(final ContentRepository contentRepository) {
        return new DeploymentAddHandler(contentRepository);
    }

    /**
     * {@inheritDoc}
     */
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        //Persistent is hidden from CLI users so let's set this to true here if it is not defined
        if (!operation.hasDefined(PERSISTENT.getName())) {
            operation.get(PERSISTENT.getName()).set(true);
        }
        boolean persistent = PERSISTENT.resolveModelAttribute(context, operation).asBoolean();

        final Resource resource = Resource.Factory.create(!persistent);
        context.addResource(PathAddress.EMPTY_ADDRESS, resource);

        ModelNode newModel = resource.getModel();
        // The 'persistent' and 'owner' parameters are hidden internal API, so handle them specifically
        PERSISTENT.validateAndSet(operation, newModel);
        OWNER.validateAndSet(operation, newModel);
        // Store the rest of the parameters that are documented parameters to this op.
        for (AttributeDefinition def : SERVER_ADD_ATTRIBUTES) {
            def.validateAndSet(operation, newModel);
        }

        // TODO: JBAS-9020: for the moment overlays are not supported, so there is a single content item
        ModelNode contentItemNode = newModel.require(CONTENT_RESOURCE_ALL.getName()).require(0);
        final ModelNode opAddr = operation.get(OP_ADDR);
        final PathAddress address = PathAddress.pathAddress(opAddr);
        final String name = address.getLastElement().getValue();
        final String runtimeName = operation.hasDefined(RUNTIME_NAME.getName()) ? operation.get(RUNTIME_NAME.getName()).asString() : name;
        newModel.get(RUNTIME_NAME.getName()).set(runtimeName);

        final DeploymentHandlerUtil.ContentItem contentItem;
        if (contentItemNode.hasDefined(CONTENT_HASH.getName())) {
            contentItem = addFromHash(contentItemNode, name, address, context);
        } else if (contentItemNode.hasDefined(EMPTY.getName())) {
            contentItem = addEmptyContentDir();
            contentItemNode = new ModelNode();
            contentItemNode.get(CONTENT_HASH.getName()).set(contentItem.getHash());
            contentItemNode.get(CONTENT_ARCHIVE.getName()).set(false);
            ModelNode content = new ModelNode();
            content.add(contentItemNode);
            newModel.get(CONTENT_RESOURCE_ALL.getName()).set(content);
        } else if(hasValidContentAdditionParameterDefined(contentItemNode)) {
            contentItem = addFromContentAdditionParameter(context, contentItemNode, name);
            // Store a hash-based contentItemNode back to the model
            contentItemNode = new ModelNode();
            contentItemNode.get(CONTENT_HASH.getName()).set(contentItem.getHash());
            ModelNode content = new ModelNode();
            content.add(contentItemNode);
            newModel.get(CONTENT_RESOURCE_ALL.getName()).set(content);
        } else {
            contentItem = addUnmanaged(contentItemNode);
        }

        if (context.getProcessType() == ProcessType.STANDALONE_SERVER) {
            // Add a step to validate uniqueness of runtime names
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    validateRuntimeNames(name, context);
                }
            }, OperationContext.Stage.MODEL);
        }

        if (ENABLED.resolveModelAttribute(context, newModel).asBoolean() && context.isNormalServer()) {
            DeploymentHandlerUtil.deploy(context, operation, runtimeName, name, contentItem);
            DeploymentUtils.enableAttribute(newModel);
        }

        if (contentItem.getHash() != null) {
            final byte[] contentHash = contentItem.getHash();
            addFlushHandler(context, contentRepository,new OperationContext.ResultHandler() {
                @Override
                public void handleResult(ResultAction resultAction, OperationContext context, ModelNode operation) {
                    if (resultAction == ResultAction.KEEP) {
                        contentRepository.addContentReference(ModelContentReference.fromModelAddress(address, contentHash));
                    }
                }
            });
        }
    }

    private void validateRuntimeNames(String deploymentName, OperationContext context) throws OperationFailedException {
        ModelNode deployment = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();

        if (ENABLED.resolveModelAttribute(context, deployment).asBoolean()) {
            String runtimeName = getRuntimeName(deploymentName, deployment);
            Resource root = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS);
            for (Resource.ResourceEntry re : root.getChildren(DEPLOYMENT)) {
                String reName = re.getName();
                if (!deploymentName.equals(reName)) {
                    ModelNode otherDepl = re.getModel();
                    if (ENABLED.resolveModelAttribute(context, otherDepl).asBoolean()) {
                        String otherRuntimeName = getRuntimeName(reName, otherDepl);
                        if (runtimeName.equals(otherRuntimeName)) {
                            throw ServerLogger.ROOT_LOGGER.runtimeNameMustBeUnique(reName, runtimeName);
                        }
                    }
                }
            }
        }
    }

    private static String getRuntimeName(String name, ModelNode deployment) {
        return deployment.hasDefined(ModelDescriptionConstants.RUNTIME_NAME)
                ? deployment.get(ModelDescriptionConstants.RUNTIME_NAME).asString() : name;
    }

    DeploymentHandlerUtil.ContentItem addFromHash(ModelNode contentItem, String deploymentName, PathAddress address, OperationContext context) throws OperationFailedException {
        return new DeploymentHandlerUtil.ContentItem(DeploymentHandlerUtil.addFromHash(contentRepository, contentItem, deploymentName, address, context), DeploymentHandlerUtil.isArchive(contentItem));
    }

    DeploymentHandlerUtil.ContentItem addEmptyContentDir() throws OperationFailedException {
        try {
            return new DeploymentHandlerUtil.ContentItem(contentRepository.addContent(null));
        } catch (IOException e) {
            throw createFailureException(e.toString());
        }
    }

    DeploymentHandlerUtil.ContentItem addFromContentAdditionParameter(OperationContext context, ModelNode contentItemNode, String name) throws OperationFailedException {
        byte[] hash;
        InputStream in = getInputStream(context, contentItemNode);
        InputStream transformed = null;
        try {
            try {
                transformed = deploymentTransformation.doTransformation(context, contentItemNode, name, in);
                hash = contentRepository.addContent(transformed);
            } catch (IOException e) {
                throw createFailureException(e.toString());
            }

        } finally {
            StreamUtils.safeClose(in);
            StreamUtils.safeClose(transformed);
        }
        return new DeploymentHandlerUtil.ContentItem(hash);
    }

    DeploymentHandlerUtil.ContentItem addUnmanaged(ModelNode contentItemNode) throws OperationFailedException {
        final String path = contentItemNode.require(CONTENT_PATH.getName()).asString();
        final String relativeTo = asString(contentItemNode, CONTENT_RELATIVE_TO.getName());
        final boolean archive = contentItemNode.require(CONTENT_ARCHIVE.getName()).asBoolean();
        return new DeploymentHandlerUtil.ContentItem(path, relativeTo, archive);
    }
}
