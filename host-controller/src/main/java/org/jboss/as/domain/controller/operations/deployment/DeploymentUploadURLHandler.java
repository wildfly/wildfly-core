/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations.deployment;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.UPLOAD_DEPLOYMENT_URL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.URL;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.repository.ContentRepository;
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

    /**
     * Constructor
     *
     * @param repository the master content repository. If {@code null} this handler will function as a slave handler would.
     */
    private DeploymentUploadURLHandler(final ContentRepository repository) {
        super(repository, DeploymentAttributes.URL_NOT_NULL);
    }

    public static void registerMaster(final ManagementResourceRegistration registration, final ContentRepository repository) {
        new DeploymentUploadURLHandler(repository).register(registration);
    }

    public static void registerSlave(final ManagementResourceRegistration registration) {
        new DeploymentUploadURLHandler(null).register(registration);
    }

    private void register(ManagementResourceRegistration registration) {
        registration.registerOperationHandler(DeploymentAttributes.DOMAIN_UPLOAD_URL_DEFINITION, this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected InputStream getContentInputStream(OperationContext operationContext, ModelNode operation) throws OperationFailedException {

        String urlSpec = operation.get(URL).asString();
        try {
            URL url = new URL(urlSpec);
            return url.openStream();
        } catch (MalformedURLException e) {
            throw new OperationFailedException(DomainControllerLogger.ROOT_LOGGER.invalidUrl(urlSpec, e.toString()));
        } catch (IOException e) {
            throw new OperationFailedException(DomainControllerLogger.ROOT_LOGGER.errorObtainingUrlStream(urlSpec, e.toString()));
        }
    }

}
