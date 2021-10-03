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

import static org.xnio.IoUtils.safeClose;

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
import org.jboss.as.server.logging.ServerLogger;
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
        try {
            attribute.validateOperation(operation);
            // Trigger authz
            AuthorizationResult authorizationResult = context.authorize(operation, ACTION_EFFECT_SET);
            authorizationResult.failIfDenied(operation, context.getCurrentAddress());

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
            throw ServerLogger.ROOT_LOGGER.caughtIOExceptionUploadingContent(e);
        }
    }

    protected abstract InputStream getContentInputStream(OperationContext context, ModelNode operation) throws IOException, OperationFailedException;

}
