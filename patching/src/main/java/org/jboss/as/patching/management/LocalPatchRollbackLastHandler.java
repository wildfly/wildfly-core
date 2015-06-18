/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.installation.InstallationManager;
import org.jboss.as.patching.installation.InstallationManagerService;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.tool.ContentVerificationPolicy;
import org.jboss.as.patching.tool.PatchOperationTarget;
import org.jboss.as.patching.tool.PatchTool;
import org.jboss.as.patching.tool.PatchingResult;
import org.jboss.dmr.ModelNode;

/**
 * @author Alexey Loubyansky
 */
public class LocalPatchRollbackLastHandler extends PatchStreamResourceOperationStepHandler {

    public static final LocalPatchRollbackLastHandler INSTANCE = new LocalPatchRollbackLastHandler();

    @Override
    protected void execute(final OperationContext context, final ModelNode operation, final InstallationManager installationManager, final String patchStream) throws OperationFailedException {

        if (installationManager.requiresRestart()) {
            throw PatchLogger.ROOT_LOGGER.serverRequiresRestart();
        }

        final boolean resetConfiguration = PatchResourceDefinition.RESET_CONFIGURATION.resolveModelAttribute(context, operation).asBoolean();

        final PatchTool runner = PatchTool.Factory.create(installationManager);
        final ContentVerificationPolicy policy = PatchTool.Factory.create(operation);
        try {
            // Rollback
            final PatchingResult result = runner.rollbackLast(patchStream, policy, resetConfiguration);
            installationManager.restartRequired();
            context.restartRequired();
            context.completeStep(new OperationContext.ResultHandler() {

                @Override
                public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                    if (resultAction == OperationContext.ResultAction.KEEP) {
                        result.commit();
                    } else {
                        installationManager.clearRestartRequired();
                        context.revertRestartRequired();
                        result.rollback();
                    }
                }

            });
        } catch (PatchingException e) {
            final ModelNode failureDescription = context.getFailureDescription();
            PatchOperationTarget.formatFailedResponse(e, failureDescription);
            installationManager.clearRestartRequired();
        } finally {
            //
        }
    }

    @Override
    protected InstallationManager getInstallationManager(OperationContext ctx) {
        return (InstallationManager) ctx.getServiceRegistry(true).getRequiredService(InstallationManagerService.NAME).getValue();
    }
}
