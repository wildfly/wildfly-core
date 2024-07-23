/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
