/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UPLOAD_DEPLOYMENT_STREAM;

import java.io.IOException;
import java.io.InputStream;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.dmr.ModelNode;

/**
* Handler for the upload-deployment-stream operation.
*
* @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
*/
public class DeploymentUploadStreamAttachmentHandler
    extends AbstractDeploymentUploadHandler {

    public static final String OPERATION_NAME = UPLOAD_DEPLOYMENT_STREAM;

    private DeploymentUploadStreamAttachmentHandler(final ContentRepository repository, final AttributeDefinition attribute) {
        super(repository, attribute);
    }

    public static void register(final ManagementResourceRegistration registration, final ContentRepository repository) {
        registration.registerOperationHandler(DeploymentAttributes.UPLOAD_STREAM_ATTACHMENT_DEFINITION,
                new DeploymentUploadStreamAttachmentHandler(repository, DeploymentAttributes.INPUT_STREAM_INDEX_NOT_NULL));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected InputStream getContentInputStream(OperationContext operationContext, ModelNode operation) throws IOException, OperationFailedException {
        // Get the attached stream
        final int streamIndex = operation.require(INPUT_STREAM_INDEX).asInt();
        final InputStream in = operationContext.getAttachmentStream(streamIndex);
        if (in == null) {
            throw ServerLogger.ROOT_LOGGER.nullStreamAttachment(streamIndex);
        }
        return in;
    }

}
