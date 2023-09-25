/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
 * @author Emanuel Muckenhuber
 */
public class LocalPatchRollbackHandler extends PatchStreamResourceOperationStepHandler {

    public static final LocalPatchRollbackHandler INSTANCE = new LocalPatchRollbackHandler();

    @Override
    protected void execute(final OperationContext context, final ModelNode operation, final InstallationManager installationManager, final String patchStream) throws OperationFailedException {

        if (installationManager.requiresRestart()) {
            throw PatchLogger.ROOT_LOGGER.serverRequiresRestart();
        }

        final String patchId = PatchResourceDefinition.PATCH_ID.resolveModelAttribute(context, operation).asString();
        final boolean rollbackTo = PatchResourceDefinition.ROLLBACK_TO.resolveModelAttribute(context, operation).asBoolean();
        final boolean restoreConfiguration = PatchResourceDefinition.RESET_CONFIGURATION.resolveModelAttribute(context, operation).asBoolean();

        final PatchTool runner = PatchTool.Factory.create(installationManager);
        final ContentVerificationPolicy policy = PatchTool.Factory.create(operation);
        try {
            // Rollback
            final PatchingResult result = runner.rollback(patchStream, patchId, policy, rollbackTo, restoreConfiguration);
            installationManager.restartRequired();
            context.restartRequired();
            context.completeStep(new OperationContext.ResultHandler() {
                @Override
                public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                    if(resultAction == OperationContext.ResultAction.KEEP) {
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
