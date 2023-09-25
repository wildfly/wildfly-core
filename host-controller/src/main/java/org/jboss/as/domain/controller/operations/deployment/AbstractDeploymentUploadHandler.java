/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations.deployment;

import static org.jboss.as.domain.controller.logging.DomainControllerLogger.ROOT_LOGGER;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.repository.ContentRepository;
import org.jboss.dmr.ModelNode;

/**
 * Base class for operation handlers that can handle the upload of deployment content.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public abstract class AbstractDeploymentUploadHandler implements OperationStepHandler {

    private static final Set<Action.ActionEffect> ACTION_EFFECT_SET =
            EnumSet.of(Action.ActionEffect.WRITE_RUNTIME);

    private final ContentRepository contentRepository;
    protected final AttributeDefinition attribute;

    protected AbstractDeploymentUploadHandler(final ContentRepository contentRepository, final AttributeDefinition attribute) {
        this.contentRepository = contentRepository;
        this.attribute = attribute;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        if (contentRepository != null) {
            // Trigger authz
            AuthorizationResult authorizationResult = context.authorize(operation, ACTION_EFFECT_SET);
            authorizationResult.failIfDenied(operation, context.getCurrentAddress());

            try {
                InputStream is = getContentInputStream(context, operation);
                try {
                    byte[] hash = contentRepository.addContent(is);
                    context.getResult().set(hash);
                }
                finally {
                    safeClose(is);
                }
            }
            catch (IOException e) {
                throw ROOT_LOGGER.caughtIOExceptionUploadingContent(e);
            }
        }
        // else this is a slave domain controller and we should ignore this operation
    }

    protected abstract InputStream getContentInputStream(OperationContext context, ModelNode operation) throws OperationFailedException;

    private static void safeClose(InputStream is) {
        if (is != null) {
            try {
                is.close();
            }
            catch (Exception e) {
                ROOT_LOGGER.caughtExceptionClosingInputStream(e);
            }
        }
    }
}
