/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

/**
 *
 */
package org.jboss.as.domain.controller.operations.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BYTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.URL;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_ARCHIVE;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_HASH;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.EXPLODED_CONTENT;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.REMOVED_PATHS;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.TARGET_PATH;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.UPDATED_PATHS;
import static org.jboss.as.server.deployment.DeploymentHandlerUtil.getContentItem;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.emptyStream;
import static org.jboss.as.server.deployment.DeploymentHandlerUtils.hasValidContentAdditionParameterDefined;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.CompositeOperationAwareTransformer;
import org.jboss.as.controller.operations.DomainOperationTransformer;
import org.jboss.as.controller.operations.OperationAttachments;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.ExplodedContent;
import org.jboss.as.repository.ExplodedContentException;
import org.jboss.as.repository.HostFileRepository;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.as.server.deployment.ModelContentReference;
import org.jboss.dmr.ModelNode;

import static org.jboss.as.server.controller.resources.DeploymentAttributes.DEPLOYMENT_CONTENT_PATH;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.EMPTY;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.OVERWRITE;

/**
 * Utility method for storing deployment content.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class DeploymentUploadUtil {

    private DeploymentUploadUtil() {
    }

    /**
     * Store the deployment contents and attach a "transformed" slave operation to the operation context.
     *
     * @param context the operation context
     * @param operation the original operation
     * @param contentRepository the content repository
     * @return the hash of the uploaded deployment content
     * @throws IOException
     * @throws OperationFailedException
     */
    public static byte[] storeContentAndTransformOperation(OperationContext context, ModelNode operation, ContentRepository contentRepository) throws IOException, OperationFailedException {
        if (!operation.hasDefined(CONTENT)) {
            throw createFailureException(DomainControllerLogger.ROOT_LOGGER.invalidContentDeclaration());
        }
        final ModelNode content = operation.get(CONTENT).get(0);
        if (content.hasDefined(HASH)) {
            // This should be handled as part of the OSH
            throw createFailureException(DomainControllerLogger.ROOT_LOGGER.invalidContentDeclaration());
        }
        final byte[] hash = storeDeploymentContent(context, operation, contentRepository);

        // Clear the contents and update with the hash
        final ModelNode slave = operation.clone();
        slave.get(CONTENT).setEmptyList().add().get(HASH).set(hash);
        // Add the domain op transformer
        List<DomainOperationTransformer> transformers = context.getAttachment(OperationAttachments.SLAVE_SERVER_OPERATION_TRANSFORMERS);
        if (transformers == null) {
            context.attach(OperationAttachments.SLAVE_SERVER_OPERATION_TRANSFORMERS, transformers = new ArrayList<>());
        }
        transformers.add(new CompositeOperationAwareTransformer(slave));
        return hash;
    }

    public static byte[] storeEmptyContentAndTransformOperation(OperationContext context, ModelNode operation, ContentRepository contentRepository) throws OperationFailedException {
        final byte[] hash = storeEmptyDeploymentContent(context, contentRepository);

        // Clear the contents and update with the hash
        final ModelNode slave = operation.clone();
        slave.remove(EMPTY.getName());
        ModelNode contentItemNode = new ModelNode();
        contentItemNode.get(CONTENT_HASH.getName()).set(hash);
        contentItemNode.get(CONTENT_ARCHIVE.getName()).set(false);
        slave.get(CONTENT).setEmptyList().add(contentItemNode);
        // Add the domain op transformer
        List<DomainOperationTransformer> transformers = context.getAttachment(OperationAttachments.SLAVE_SERVER_OPERATION_TRANSFORMERS);
        if (transformers == null) {
            context.attach(OperationAttachments.SLAVE_SERVER_OPERATION_TRANSFORMERS, transformers = new ArrayList<>());
        }
        transformers.add(new CompositeOperationAwareTransformer(slave));
        return hash;
    }

    /**
     * Explode the deployment contents and attach a "transformed" slave operation to the operation context.
     *
     * @param context the operation context
     * @param operation the original operation
     * @param contentRepository the content repository
     * @return the hash of the uploaded deployment content
     * @throws IOException
     * @throws OperationFailedException
     */
    public static byte[] explodeContentAndTransformOperation(OperationContext context, ModelNode operation, ContentRepository contentRepository) throws OperationFailedException, ExplodedContentException {
        final Resource deploymentResource = context.readResource(PathAddress.EMPTY_ADDRESS);
        ModelNode contentItem = getContentItem(deploymentResource);
        ModelNode explodedPath = DEPLOYMENT_CONTENT_PATH.resolveModelAttribute(context, operation);
        byte[] oldHash = CONTENT_HASH.resolveModelAttribute(context, contentItem).asBytes();
        final byte[] hash;
        if (explodedPath.isDefined()) {
            hash = contentRepository.explodeSubContent(oldHash, explodedPath.asString());
        } else {
            hash = contentRepository.explodeContent(oldHash);
        }

        // Clear the contents and update with the hash
        final ModelNode slave = operation.clone();
        ModelNode addedContent = new ModelNode().setEmptyObject();
        addedContent.get(HASH).set(hash);
        addedContent.get(TARGET_PATH.getName()).set("./");
        slave.get(CONTENT).setEmptyList().add(addedContent);
        // Add the domain op transformer
        List<DomainOperationTransformer> transformers = context.getAttachment(OperationAttachments.SLAVE_SERVER_OPERATION_TRANSFORMERS);
        if (transformers == null) {
            context.attach(OperationAttachments.SLAVE_SERVER_OPERATION_TRANSFORMERS, transformers = new ArrayList<>());
        }
        transformers.add(new CompositeOperationAwareTransformer(slave));
        return hash;
    }

    /**
     * Add contents to the deployment and attach a "transformed" slave operation to the operation context.
     *
     * @param context the operation context
     * @param operation the original operation
     * @param contentRepository the content repository
     * @return the hash of the uploaded deployment content
     * @throws IOException
     * @throws OperationFailedException
     */
    public static byte[] addContentToExplodedAndTransformOperation(OperationContext context, ModelNode operation, ContentRepository contentRepository) throws OperationFailedException, ExplodedContentException {
        final Resource deploymentResource = context.readResource(PathAddress.EMPTY_ADDRESS);
        ModelNode contentItem = getContentItem(deploymentResource);
        byte[] oldHash = CONTENT_HASH.resolveModelAttribute(context, contentItem).asBytes();
        List<ModelNode> contents = EXPLODED_CONTENT.resolveModelAttribute(context, operation).asList();
        final List<ExplodedContent> addedFiles = new ArrayList<>(contents.size());

        final ModelNode slave = operation.clone();
        ModelNode slaveAddedfiles = slave.get(UPDATED_PATHS.getName()).setEmptyList();
        for(ModelNode content : contents) {
            InputStream in;
            if(hasValidContentAdditionParameterDefined(content)) {
                in = getInputStream(context, content);
            } else {
                in = emptyStream();
            }
            String path = TARGET_PATH.resolveModelAttribute(context, content).asString();
            addedFiles.add(new ExplodedContent(path, in));
            slaveAddedfiles.add(path);
        }
        final boolean overwrite = OVERWRITE.resolveModelAttribute(context, operation).asBoolean(true);
        final byte[] hash = contentRepository.addContentToExploded(oldHash, addedFiles, overwrite);

        // Clear the contents and update with the hash
        ModelNode addedContent = new ModelNode().setEmptyObject();
        addedContent.get(HASH).set(hash);
        addedContent.get(TARGET_PATH.getName()).set(".");
        slave.get(CONTENT).setEmptyList().add(addedContent);
        // Add the domain op transformer
        List<DomainOperationTransformer> transformers = context.getAttachment(OperationAttachments.SLAVE_SERVER_OPERATION_TRANSFORMERS);
        if (transformers == null) {
            context.attach(OperationAttachments.SLAVE_SERVER_OPERATION_TRANSFORMERS, transformers = new ArrayList<>());
        }
        transformers.add(new CompositeOperationAwareTransformer(slave));
        return hash;
    }

    /**
     * Remove contents from the deployment and attach a "transformed" slave operation to the operation context.
     *
     * @param context the operation context
     * @param operation the original operation
     * @param contentRepository the content repository
     * @return the hash of the uploaded deployment content
     * @throws IOException
     * @throws OperationFailedException
     */
    public static byte[] removeContentFromExplodedAndTransformOperation(OperationContext context, ModelNode operation, ContentRepository contentRepository) throws OperationFailedException, ExplodedContentException {
        final Resource deploymentResource = context.readResource(PathAddress.EMPTY_ADDRESS);
        ModelNode contentItemNode = getContentItem(deploymentResource);
        final byte[] oldHash = CONTENT_HASH.resolveModelAttribute(context, contentItemNode).asBytes();
        final List<String> paths = REMOVED_PATHS.unwrap(context, operation);
        final byte[] hash = contentRepository.removeContentFromExploded(oldHash, paths);

        // Clear the contents and update with the hash
        final ModelNode slave = operation.clone();
        slave.get(CONTENT).setEmptyList().add().get(HASH).set(hash);
        slave.get(CONTENT).add().get(ARCHIVE).set(false);
        // Add the domain op transformer
        List<DomainOperationTransformer> transformers = context.getAttachment(OperationAttachments.SLAVE_SERVER_OPERATION_TRANSFORMERS);
        if (transformers == null) {
            context.attach(OperationAttachments.SLAVE_SERVER_OPERATION_TRANSFORMERS, transformers = new ArrayList<>());
        }
        transformers.add(new CompositeOperationAwareTransformer(slave));
        return hash;
    }

    /**
     * Synchronize the required files to a slave HC from the master DC if this is required.
     * @param fileRepository the HostFileRepository of the HC.
     * @param contentRepository the ContentRepository of the HC.
     * @param backup inidcates if this is a DC backup HC.
     * @param oldHash the hash of the deployment to be replaced.
     * @return true if the content should be pulled by the slave HC - false otherwise.
     */
    public static byte[] synchronizeSlaveHostController(ModelNode operation, final PathAddress address, HostFileRepository fileRepository, ContentRepository contentRepository, boolean backup, byte[] oldHash) {
        ModelNode operationContentItem = operation.get(DeploymentAttributes.CONTENT_RESOURCE.getName()).get(0);
        byte[] newHash = operationContentItem.require(CONTENT_HASH.getName()).asBytes();
        if (needRemoteContent(fileRepository, contentRepository, backup, oldHash)) {  // backup DC needs to  pull the content
            fileRepository.getDeploymentFiles(ModelContentReference.fromModelAddress(address, newHash));
        }
        return newHash;
    }

    private static boolean needRemoteContent(HostFileRepository fileRepository, ContentRepository contentRepository, boolean backup, byte[] oldHash) {
        return fileRepository != null && (backup  || (contentRepository != null && contentRepository.hasContent(oldHash)));
    }

    private static byte[] storeDeploymentContent(OperationContext context, ModelNode operation, ContentRepository contentRepository) throws IOException, OperationFailedException {
        try (InputStream in = getContents(context, operation)){
            return contentRepository.addContent(in);
        }
    }

    private static byte[] storeEmptyDeploymentContent(OperationContext context, ContentRepository contentRepository) throws OperationFailedException {
        try {
            return contentRepository.addContent(null);
        } catch (IOException e) {
            throw createFailureException(e.toString());
        }
    }

    private static InputStream getContents(OperationContext context, ModelNode operation) throws OperationFailedException {
        if(! operation.hasDefined(CONTENT)) {
            throw createFailureException(DomainControllerLogger.ROOT_LOGGER.invalidContentDeclaration());
        }
        return getInputStream(context, operation.require(CONTENT).get(0));
    }

    private static InputStream getInputStream(OperationContext context, ModelNode content) throws OperationFailedException {
        InputStream in = null;
        String message = "";
        if (content.hasDefined(INPUT_STREAM_INDEX)) {
            int streamIndex = content.get(INPUT_STREAM_INDEX).asInt();
            if (streamIndex > context.getAttachmentStreamCount() - 1) {
                message = DomainControllerLogger.ROOT_LOGGER.invalidValue(INPUT_STREAM_INDEX, streamIndex, (context.getAttachmentStreamCount() - 1));
                throw createFailureException(message);
            }
            message = DomainControllerLogger.ROOT_LOGGER.nullStream(streamIndex);
            in = context.getAttachmentStream(streamIndex);
        } else if (content.hasDefined(BYTES)) {
            in = new ByteArrayInputStream(content.get(BYTES).asBytes());
            message = DomainControllerLogger.ROOT_LOGGER.invalidByteStream();
        } else if (content.hasDefined(URL)) {
            final String urlSpec = content.get(URL).asString();
            try {
                message = DomainControllerLogger.ROOT_LOGGER.invalidUrlStream();
                in = new URL(urlSpec).openStream();
            } catch (MalformedURLException e) {
                throw createFailureException(message);
            } catch (IOException e) {
                throw createFailureException(message);
            }
        }
        if (in == null) {
            throw createFailureException(message);
        }
        return in;
    }

    private static OperationFailedException createFailureException(String msg) {
        return new OperationFailedException(msg);
    }
}
