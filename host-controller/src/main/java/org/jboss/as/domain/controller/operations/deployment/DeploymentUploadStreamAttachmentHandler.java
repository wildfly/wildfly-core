/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INPUT_STREAM_INDEX;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UPLOAD_DEPLOYMENT_STREAM;

import java.io.InputStream;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.repository.ContentRepository;
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


    /**
     * Constructor
     *
     * @param repository the master content repository. If {@code null} this handler will function as a slave hander would.
     */
    private DeploymentUploadStreamAttachmentHandler(final ContentRepository repository) {
        super(repository, DeploymentAttributes.INPUT_STREAM_INDEX_NOT_NULL);
    }

    public static void registerMaster(final ManagementResourceRegistration registration, final ContentRepository repository) {
        new DeploymentUploadStreamAttachmentHandler(repository).register(registration);
    }

    public static void registerSlave(final ManagementResourceRegistration registration) {
        new DeploymentUploadStreamAttachmentHandler(null).register(registration);
    }

    private void register(ManagementResourceRegistration registration) {
        registration.registerOperationHandler(DeploymentAttributes.DOMAIN_UPLOAD_STREAM_ATTACHMENT_DEFINITION, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected InputStream getContentInputStream(OperationContext operationContext, ModelNode operation) throws OperationFailedException {
        int streamIndex = operation.get(INPUT_STREAM_INDEX).asInt();
        int maxIndex = operationContext.getAttachmentStreamCount();
        if (streamIndex > maxIndex) {
            throw new OperationFailedException(DomainControllerLogger.ROOT_LOGGER.invalidValue(INPUT_STREAM_INDEX, streamIndex, maxIndex));
        }

        InputStream in = operationContext.getAttachmentStream(streamIndex);
        if (in == null) {
            throw new OperationFailedException(DomainControllerLogger.ROOT_LOGGER.nullStream(streamIndex));
        }

        return in;
    }

}
