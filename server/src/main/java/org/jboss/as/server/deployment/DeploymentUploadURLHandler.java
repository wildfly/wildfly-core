/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UPLOAD_DEPLOYMENT_URL;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.dmr.ModelNode;

/**
 * Handler for the upload-deployment-url operation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DeploymentUploadURLHandler
    extends AbstractDeploymentUploadHandler {

    public static final String OPERATION_NAME = UPLOAD_DEPLOYMENT_URL;

    private DeploymentUploadURLHandler(final ContentRepository repository, final AttributeDefinition attribute) {
        super(repository, attribute);
    }


    public static void register(final ManagementResourceRegistration registration, final ContentRepository repository) {
        registration.registerOperationHandler(DeploymentAttributes.UPLOAD_URL_DEFINITION,
                new DeploymentUploadURLHandler(repository, DeploymentAttributes.URL_NOT_NULL));
    }


    /**
     * {@inheritDoc}
     */
    @Override
    protected InputStream getContentInputStream(OperationContext operationContext, ModelNode operation) throws OperationFailedException {

        String urlSpec = attribute.resolveModelAttribute(operationContext, operation).asString();
        try {
            URL url = new URL(urlSpec);
            return url.openStream();
        } catch (MalformedURLException e) {
            throw ServerLogger.ROOT_LOGGER.invalidDeploymentURL(urlSpec, e);
        } catch (IOException e) {
            throw ServerLogger.ROOT_LOGGER.problemOpeningStreamFromDeploymentURL(urlSpec, e);
        }
    }

}
