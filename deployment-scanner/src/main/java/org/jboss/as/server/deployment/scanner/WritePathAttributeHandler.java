/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.scanner;

import static org.jboss.as.server.deployment.scanner.logging.DeploymentScannerLogger.ROOT_LOGGER;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.dmr.ModelNode;

/**
 * @author wangc
 *
 */
public class WritePathAttributeHandler extends ReloadRequiredWriteAttributeHandler {

    private final PathManager pathManager;

    public WritePathAttributeHandler(PathManager pathManager) {
        this.pathManager = pathManager;
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
            ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> voidHandback)
                    throws OperationFailedException {
        boolean reloadRequired = super.applyUpdateToRuntime(context, operation, attributeName, resolvedValue, currentValue,
                voidHandback);
        if (reloadRequired) {
            // The value actually changed so we need to add step to validate the path
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    final ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
                    final ModelNode relativeTo = DeploymentScannerDefinition.RELATIVE_TO.resolveModelAttribute(context, model);
                    Path fullPath;
                    if (relativeTo.isDefined()) {
                        final String fullPathName = pathManager.resolveRelativePathEntry(resolvedValue.asString(),
                                relativeTo.asString());
                        fullPath = Paths.get(fullPathName);
                    } else {
                        fullPath = Paths.get(resolvedValue.asString());
                    }

                    if (!Files.exists(fullPath)) {
                        ROOT_LOGGER.directoryIsNonexistent(fullPath.toString());
                    } else if (!Files.isDirectory(fullPath)) {
                        ROOT_LOGGER.isNotADirectory(fullPath.toString());
                    } else if (!Files.isReadable(fullPath)) {
                        ROOT_LOGGER.directoryIsNotReadable(fullPath.toString());
                    } else if (!Files.isWritable(fullPath)) {
                        ROOT_LOGGER.directoryIsNotWritable(fullPath.toString());
                    }
                }
            }, Stage.RUNTIME);
        }
        return reloadRequired;
    }
}
