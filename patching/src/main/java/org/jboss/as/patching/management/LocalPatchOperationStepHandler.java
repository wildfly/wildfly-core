/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.management;

import java.io.InputStream;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.installation.InstallationManager;
import org.jboss.as.patching.installation.InstallationManagerService;
import org.jboss.as.patching.logging.PatchLogger;
import org.jboss.as.patching.tool.ContentVerificationPolicy;
import org.jboss.as.patching.tool.PatchOperationTarget;
import org.jboss.as.patching.tool.PatchTool;
import org.jboss.as.patching.tool.PatchingResult;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceRegistry;

/**
 * @author Emanuel Muckenhuber
 */
public final class LocalPatchOperationStepHandler implements OperationStepHandler {
    public static final OperationStepHandler INSTANCE = new LocalPatchOperationStepHandler();

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        if (context.getCurrentStage() == OperationContext.Stage.MODEL) {
            context.addStep(this::executeRuntime, OperationContext.Stage.RUNTIME);
        } else {
            executeRuntime(context, operation);
        }
    }

    private void executeRuntime(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        // Acquire the lock and check the write permissions for this operation
        final ServiceRegistry registry = context.getServiceRegistry(true);
        final InstallationManager installationManager = (InstallationManager) registry.getRequiredService(InstallationManagerService.NAME).getValue();

        if (installationManager.requiresRestart()) {
            throw PatchLogger.ROOT_LOGGER.serverRequiresRestart();
        }

        try {
            final PatchTool runner = PatchTool.Factory.create(installationManager);
            final ContentVerificationPolicy policy = PatchTool.Factory.create(operation);

            final int index = operation.get(ModelDescriptionConstants.INPUT_STREAM_INDEX).asInt(0);
            final InputStream is = context.getAttachmentStream(index);
            installationManager.restartRequired();
            final PatchingResult result = runner.applyPatch(is, policy);
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
        }
    }

}
