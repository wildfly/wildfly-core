/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deploymentoverlay;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BYTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.URL;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.operations.CompositeOperationAwareTransmuter;
import org.jboss.as.controller.operations.OperationAttachments;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.DeploymentFileRepository;
import org.jboss.as.server.deployment.ModelContentReference;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.as.controller.operations.DomainOperationTransmuter;

/**
 * @author Stuart Douglas
 */
public class DeploymentOverlayContentAdd extends AbstractAddStepHandler {

    protected final ContentRepository contentRepository;
    private final DeploymentFileRepository remoteRepository;


    public DeploymentOverlayContentAdd(final ContentRepository contentRepository, final DeploymentFileRepository remoteRepository) {
        this.contentRepository = contentRepository;
        this.remoteRepository = remoteRepository;
    }

    @Override
    protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {
        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        final String path = address.getLastElement().getValue();
        final String name = address.getElement(address.size() - 2).getValue();
        final ModelNode content = DeploymentOverlayContentDefinition.CONTENT_PARAMETER.validateOperation(operation);
        validateOnePieceOfContent(content);
        final byte[] hash;
        if (content.hasDefined(HASH)) {
            hash = content.require(HASH).asBytes();
            addFromHash(hash, name, path, address, context);
        } else {
            hash = addFromContentAdditionParameter(context, content);

            final ModelNode slave = operation.clone();
            slave.get(CONTENT).clear();
            slave.get(CONTENT).get(HASH).set(hash);

            List<DomainOperationTransmuter> transformers = context.getAttachment(OperationAttachments.SLAVE_SERVER_OPERATION_TRANSMUTERS);
            if(transformers == null) {
                context.attach(OperationAttachments.SLAVE_SERVER_OPERATION_TRANSMUTERS, transformers = new ArrayList<DomainOperationTransmuter>());
            }
            transformers.add(new CompositeOperationAwareTransmuter(slave));
        }
        contentRepository.addContentReference(ModelContentReference.fromModelAddress(address, hash));

        ModelNode modified = operation.clone();
        modified.get(CONTENT).clone();
        modified.get(CONTENT).set(hash);
        for (AttributeDefinition attr : DeploymentOverlayContentDefinition.attributes()) {
            attr.validateAndSet(modified, resource.getModel());
        }
        if (!contentRepository.syncContent(ModelContentReference.fromModelAddress(address, hash))) {
            throw ServerLogger.ROOT_LOGGER.noSuchDeploymentContent(Arrays.toString(hash));
        }
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return false;
    }

    protected static void validateOnePieceOfContent(final ModelNode content) throws OperationFailedException {
        if (content.asList().size() != 1)
            throw ServerLogger.ROOT_LOGGER.multipleContentItemsNotSupported();
    }

    byte[] addFromHash(byte[] hash, String deploymentOverlayName, final String contentName, final PathAddress address, final OperationContext context) throws OperationFailedException {
        ContentReference reference = ModelContentReference.fromModelAddress(address, hash);
        if(remoteRepository != null) {
            remoteRepository.getDeploymentFiles(reference);
        }
        if (!contentRepository.syncContent(reference)) {
            if (context.isBooting()) {
                if (context.getRunningMode() == RunningMode.ADMIN_ONLY) {
                    // The deployment content is missing, which would be a fatal boot error if we were going to actually
                    // install services. In ADMIN-ONLY mode we allow it to give the admin a chance to correct the problem
                    ServerLogger.ROOT_LOGGER.reportAdminOnlyMissingDeploymentOverlayContent(HashUtil.bytesToHexString(hash), deploymentOverlayName, contentName);

                } else {
                    throw ServerLogger.ROOT_LOGGER.noSuchDeploymentOverlayContentAtBoot(HashUtil.bytesToHexString(hash), deploymentOverlayName, contentName);
                }
            } else {
                throw ServerLogger.ROOT_LOGGER.noSuchDeploymentOverlayContent(HashUtil.bytesToHexString(hash));
            }
        }
        return hash;
    }

    byte[] addFromContentAdditionParameter(OperationContext context, ModelNode contentItemNode) throws OperationFailedException {
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
        return hash;
    }

    protected static OperationFailedException createFailureException(String msg) {
        return new OperationFailedException(msg);
    }

    protected static InputStream getInputStream(OperationContext context, ModelNode operation) throws OperationFailedException {
        InputStream in = null;
        if (operation.hasDefined(INPUT_STREAM_INDEX)) {
            int streamIndex = operation.get(INPUT_STREAM_INDEX).asInt();
            int maxIndex = context.getAttachmentStreamCount();
            if (streamIndex > maxIndex) {
                throw ServerLogger.ROOT_LOGGER.invalidStreamIndex(INPUT_STREAM_INDEX, streamIndex, maxIndex);
            }
            in = context.getAttachmentStream(streamIndex);
        } else if (operation.hasDefined(BYTES)) {
            try {
                in = new ByteArrayInputStream(operation.get(BYTES).asBytes());
            } catch (IllegalArgumentException iae) {
                throw ServerLogger.ROOT_LOGGER.invalidStreamBytes(BYTES);
            }
        } else if (operation.hasDefined(URL)) {
            final String urlSpec = operation.get(URL).asString();
            try {
                in = new java.net.URL(urlSpec).openStream();
            } catch (MalformedURLException e) {
                throw ServerLogger.ROOT_LOGGER.invalidStreamURL(e, urlSpec);
            } catch (IOException e) {
                throw ServerLogger.ROOT_LOGGER.invalidStreamURL(e, urlSpec);
            }
        }
        if (in == null) {
            // Won't happen, as we call hasValidContentAdditionParameterDefined first
            throw new IllegalStateException();
        }
        return in;
    }
}
