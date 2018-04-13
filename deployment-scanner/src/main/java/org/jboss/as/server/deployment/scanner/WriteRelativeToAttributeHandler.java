/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.deployment.scanner;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.server.deployment.scanner.logging.DeploymentScannerLogger;
import org.jboss.dmr.ModelNode;

/**
 * @author wangc
 *
 */
public class WriteRelativeToAttributeHandler extends ReloadRequiredWriteAttributeHandler {

    private final PathManager pathManager;

    public WriteRelativeToAttributeHandler(PathManager pathManager) {
        super(DeploymentScannerDefinition.RELATIVE_TO);
        this.pathManager = pathManager;
    }

    @Override
    protected void validateUpdatedModel(final OperationContext context, final Resource resource)
            throws OperationFailedException {
        final ModelNode model = resource.getModel();
        final ModelNode relativeTo = DeploymentScannerDefinition.RELATIVE_TO.resolveModelAttribute(context, model);
        if (relativeTo.isDefined()) {
            try {
                pathManager.getPathEntry(relativeTo.asString());
            } catch (IllegalArgumentException e) {
                throw DeploymentScannerLogger.ROOT_LOGGER.pathEntryNotFound(relativeTo.asString());
            }
        }
    }
}
