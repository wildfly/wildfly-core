/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.management;

import java.io.IOException;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.patching.installation.Identity;
import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.dmr.ModelNode;

abstract class PatchAttributeReadHandler extends PatchStreamResourceOperationStepHandler {

    PatchAttributeReadHandler() {
        this.acquireWriteLock = false;
    }

    @Override
    protected void execute(final OperationContext context, final ModelNode operation, final InstalledIdentity installedIdentity) throws OperationFailedException {

        final ModelNode result = context.getResult();
        final Identity info = installedIdentity.getIdentity();
        try {
            handle(result, info);
        } catch (IOException e) {
            throw new OperationFailedException(PatchLogger.ROOT_LOGGER.failedToLoadInfo(info.getName()), e);
        }
        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
    }

    abstract void handle(ModelNode result, Identity info) throws IOException;
}
