/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.management;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.patching.Constants;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.installation.InstallationManager;
import org.jboss.as.patching.installation.InstallationManagerService;
import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;

/**
 *
 * @author Alexey Loubyansky
 */
abstract class PatchStreamResourceOperationStepHandler implements OperationStepHandler {
    boolean acquireWriteLock = true;

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        if (context.getCurrentStage() == OperationContext.Stage.MODEL) {
            context.addStep(this::executeRuntime, OperationContext.Stage.RUNTIME);
        } else {
            executeRuntime(context, operation);
        }
    }

    private void executeRuntime(OperationContext context, ModelNode operation) throws OperationFailedException {
        if (acquireWriteLock) {
            context.acquireControllerLock();
        }
        execute(context, operation, getInstallationManager(context), getPatchStreamName(context));
    }

    protected String getPatchStreamName(OperationContext context) {
        final PathElement stream = context.getCurrentAddress().getLastElement();
        final String streamName;
        if(Constants.PATCH_STREAM.equals(stream.getKey())) {
            streamName = stream.getValue();
        } else {
            streamName = null;
        }
        return streamName;
    }

    protected void execute(OperationContext context, ModelNode operation, InstallationManager instMgr, String patchStream) throws OperationFailedException {
        final InstalledIdentity installedIdentity;
        if(patchStream != null) {
            try {
                installedIdentity = instMgr.getInstalledIdentity(patchStream, null);
            } catch (PatchingException e) {
                throw new OperationFailedException(PatchLogger.ROOT_LOGGER.failedToLoadInfo(patchStream), e);
            }
        } else {
            installedIdentity = instMgr.getDefaultIdentity();
        }
        execute(context, operation, installedIdentity);
    }

    protected void execute(OperationContext context, ModelNode operation, InstalledIdentity installedIdentity) throws OperationFailedException {
        throw new UnsupportedOperationException();
    }

    protected InstallationManager getInstallationManager(OperationContext ctx) {
        final ServiceController<?> imController = ctx.getServiceRegistry(false).getRequiredService(InstallationManagerService.NAME);
        while (imController != null && imController.getState() == ServiceController.State.UP) {
            try {
                return (InstallationManager) imController.getValue();
            } catch (IllegalStateException e) {
                // ignore, caused by race from WFLY-3505
            }
        }
        return null;
    }
}
