/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
