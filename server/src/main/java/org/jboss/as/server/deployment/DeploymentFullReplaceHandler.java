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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FULL_REPLACE_DEPLOYMENT;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_ARCHIVE;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_HASH;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_PATH;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_RELATIVE_TO;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.ENABLED;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.OWNER;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.PERSISTENT;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.RUNTIME_NAME;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.asString;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.createFailureException;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.getInputStream;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.hasValidContentAdditionParameterDefined;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.ResultAction;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.services.security.AbstractVaultReader;
import org.jboss.dmr.ModelNode;

/**
 * Handles replacement in the runtime of one deployment by another.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DeploymentFullReplaceHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = FULL_REPLACE_DEPLOYMENT;

    protected final ContentRepository contentRepository;

    private final AbstractVaultReader vaultReader;

    protected DeploymentFullReplaceHandler(final ContentRepository contentRepository, final AbstractVaultReader vaultReader) {
        assert contentRepository != null : "Null contentRepository";
        this.contentRepository = contentRepository;
        this.vaultReader = vaultReader;
    }

    public static DeploymentFullReplaceHandler create(final ContentRepository contentRepository, final AbstractVaultReader vaultReader) {
        return new DeploymentFullReplaceHandler(contentRepository, vaultReader);
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
        final String runtimeName = correctedOperation.hasDefined(RUNTIME_NAME.getName()) ? correctedOperation.get(RUNTIME_NAME.getName()).asString() : name;
        // clone the content param, so we can modify it to our own content
        ModelNode content = correctedOperation.require(CONTENT).clone();

        // Throw a specific exception if the replaced deployment doesn't already exist
        // BES 2013/10/30 -- this is pointless; the readResourceForUpdate call will throw
        // an exception with an equally informative message if the deployment doesn't exist
//        final Resource root = context.readResource(PathAddress.EMPTY_ADDRESS);
//        boolean exists = root.hasChild(deploymentPath);
//        if (!exists) {
//            throw ServerLogger.ROOT_LOGGER.noSuchDeployment(name);
//        }

        // verify that the resource existance before removing it
        context.readResourceForUpdate(PathAddress.pathAddress(deploymentPath));
        // WFCORE-495 remove and call context.addResource() as below to add new resource with updated PERSISTENT value
        final ModelNode deploymentModel = context.removeResource(PathAddress.pathAddress(deploymentPath)).getModel();
        final ModelNode originalDeployment = deploymentModel.clone();

        // Keep track of runtime name of deployment we are replacing for use in Stage.RUNTIME
        final String replacedRuntimeName = RUNTIME_NAME.resolveModelAttribute(context, deploymentModel).asString();
        final PathAddress address = PathAddress.pathAddress(deploymentPath);
        // Keep track of hash we are replacing so we can drop it from the content repo if all is well
        ModelNode replacedContent = deploymentModel.get(CONTENT).get(0);
        final byte[] replacedHash = replacedContent.hasDefined(CONTENT_HASH.getName())
                ? CONTENT_HASH.resolveModelAttribute(context, replacedContent).asBytes() : null;

        // Set up the new content attribute
        final byte[] newHash;
        // TODO: JBAS-9020: for the moment overlays are not supported, so there is a single content item
        final DeploymentHandlerUtil.ContentItem contentItem;
        ModelNode contentItemNode = content.require(0);
        if (contentItemNode.hasDefined(CONTENT_HASH.getName())) {
            newHash = CONTENT_HASH.resolveModelAttribute(context, contentItemNode).asBytes();
            ContentReference reference = ModelContentReference.fromModelAddress(address, newHash);
            contentItem = addFromHash(reference);
        } else if (hasValidContentAdditionParameterDefined(contentItemNode)) {
            contentItem = addFromContentAdditionParameter(context, contentItemNode);
            newHash = contentItem.getHash();

            // Replace the content data
            contentItemNode = new ModelNode();
            contentItemNode.get(CONTENT_HASH.getName()).set(newHash);
            content.clear();
            content.add(contentItemNode);
        } else {
            contentItem = addUnmanaged(context, contentItemNode);
            newHash = null;
        }

        // deploymentModel.get(NAME).set(name); // already there
        deploymentModel.get(RUNTIME_NAME.getName()).set(runtimeName);
        deploymentModel.get(CONTENT).set(content);
        // The 'persistent' and 'owner' parameters are hidden internal API, so handle them specifically
        // Persistent is hidden from CLI users so let's set this to true here if it is not defined
        if (!operation.hasDefined(PERSISTENT.getName())) {
            operation.get(PERSISTENT.getName()).set(true);
        }
        PERSISTENT.validateAndSet(operation, deploymentModel);
        OWNER.validateAndSet(operation, deploymentModel);

        // ENABLED stays as is if not present in operation
        boolean wasDeployed = ENABLED.resolveModelAttribute(context, deploymentModel).asBoolean();
        if (operation.hasDefined(ENABLED.getName())) {
            ENABLED.validateAndSet(operation, deploymentModel);
        }

        // Do the runtime part if the deployment is enabled
        if (ENABLED.resolveModelAttribute(context, deploymentModel).asBoolean()) {
            DeploymentUtils.enableAttribute(deploymentModel);
        } else if (wasDeployed) {
            DeploymentUtils.disableAttribute(deploymentModel);
        }

        boolean persistent = PERSISTENT.resolveModelAttribute(context, operation).asBoolean();
        final Resource resource = Resource.Factory.create(!persistent);
        resource.writeModel(deploymentModel);
        context.addResource(PathAddress.pathAddress(deploymentPath), resource);
        if (newHash != null) {
            contentRepository.addContentReference(ModelContentReference.fromModelAddress(address, newHash));
        }

        if (ENABLED.resolveModelAttribute(context, deploymentModel).asBoolean()) {
            DeploymentHandlerUtil.replace(context, originalDeployment, runtimeName, name, replacedRuntimeName, vaultReader, contentItem);
        } else if (wasDeployed) {
            DeploymentHandlerUtil.undeploy(context, operation, name, runtimeName, vaultReader);
        }

        context.completeStep(new OperationContext.ResultHandler() {
            @Override
            public void handleResult(ResultAction resultAction, OperationContext context, ModelNode operation) {
                if (resultAction == ResultAction.KEEP) {
                    if (replacedHash != null  && (newHash == null || !Arrays.equals(replacedHash, newHash))) {
                        // The old content is no longer used; clean from repos
                        contentRepository.removeContent(ModelContentReference.fromModelAddress(address, replacedHash));
                    }
                } else if (newHash != null && (replacedHash == null || !Arrays.equals(replacedHash, newHash))) {
                    // Due to rollback, the new content isn't used; clean from repos
                    contentRepository.removeContent(ModelContentReference.fromModelAddress(address, newHash));
                }
            }
        });
    }

    DeploymentHandlerUtil.ContentItem addFromHash(ContentReference reference) throws OperationFailedException {
        if (!contentRepository.syncContent(reference)) {
            throw ServerLogger.ROOT_LOGGER.noSuchDeploymentContent(reference.getHexHash());
        }
        return new DeploymentHandlerUtil.ContentItem(reference.getHash());
    }

    DeploymentHandlerUtil.ContentItem addFromContentAdditionParameter(OperationContext context, ModelNode contentItemNode) throws OperationFailedException {
        byte[] hash;
        InputStream in = getInputStream(context, contentItemNode);
        try {
            try {
                hash = contentRepository.addContent(in);
            } catch (IOException e) {
                throw createFailureException(e.toString());
            }

        } finally {
            StreamUtils.safeClose(in);
        }
        contentItemNode.clear(); // AS7-1029
        contentItemNode.get(CONTENT_HASH.getName()).set(hash);
        // TODO: remove the content addition stuff?
        return new DeploymentHandlerUtil.ContentItem(hash);
    }

    DeploymentHandlerUtil.ContentItem addUnmanaged(OperationContext context, ModelNode contentItemNode) throws OperationFailedException {
        final String path = CONTENT_PATH.resolveModelAttribute(context, contentItemNode).asString();
        final String relativeTo = asString(contentItemNode, CONTENT_RELATIVE_TO.getName());
        final boolean archive = CONTENT_ARCHIVE.resolveModelAttribute(context, contentItemNode).asBoolean();
        return new DeploymentHandlerUtil.ContentItem(path, relativeTo, archive);
    }

}
