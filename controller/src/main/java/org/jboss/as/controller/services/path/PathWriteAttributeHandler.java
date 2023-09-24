/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.services.path;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELATIVE_TO;
import static org.jboss.as.controller.services.path.PathResourceDefinition.READ_ONLY;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager.Event;
import org.jboss.as.controller.services.path.PathManagerService.PathEventContextImpl;
import org.jboss.dmr.ModelNode;

/**
 * {@link org.jboss.as.controller.OperationStepHandler} for the write-attribute operation for a path resource.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class PathWriteAttributeHandler extends AbstractWriteAttributeHandler<PathWriteAttributeHandler.PathUpdate> {

    private final PathManagerService pathManager;

    PathWriteAttributeHandler(final PathManagerService pathManager, final SimpleAttributeDefinition definition) {
        super(definition);
        this.pathManager = pathManager;
    }

    @Override
    protected void finishModelStage(OperationContext context, ModelNode operation, String attributeName,
                                    ModelNode newValue, ModelNode oldValue, Resource model) throws OperationFailedException {
        // Guard against updates to read-only paths
        final String pathName = context.getCurrentAddressValue();
        if (model.getModel().get(READ_ONLY.getName()).asBoolean(false)) {
            throw ControllerLogger.ROOT_LOGGER.cannotModifyReadOnlyPath(pathName);
        }
        if (pathManager != null) {
            final PathEntry pathEntry = pathManager.getPathEntry(pathName);
            if (pathEntry.isReadOnly()) {
                throw ControllerLogger.ROOT_LOGGER.pathEntryIsReadOnly(operation.require(OP_ADDR).asString());
            }
        }
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return pathManager != null;
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                           ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<PathUpdate> handbackHolder) throws OperationFailedException {
        final String pathName = context.getCurrentAddressValue();
        final PathEntry pathEntry = pathManager.getPathEntry(pathName);
        final PathEntry backup = new PathEntry(pathEntry);

        final PathEventContextImpl pathEventContext = pathManager.checkRestartRequired(context, pathName, Event.UPDATED);
        if (pathEventContext.isInstallServices()) {
            if (attributeName.equals(PATH)) {
                String pathVal = resolvedValue.asString();
                pathManager.changePath(pathName, pathVal);
                pathManager.changePathServices(context, pathName, pathVal);
            } else if (attributeName.equals(RELATIVE_TO)) {
                String relToVal = resolvedValue.isDefined() ?  resolvedValue.asString() : null;
                pathManager.changeRelativePath( pathName, relToVal, true);
                pathManager.changeRelativePathServices(context, pathName, relToVal);
            }
        }

        handbackHolder.setHandback(new PathUpdate(backup, pathEventContext));

        return false;
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                         ModelNode valueToRestore, ModelNode valueToRevert, PathUpdate handback) throws OperationFailedException {
        final String pathName = context.getCurrentAddressValue();
        final PathEntry backup = handback.backup;
        final PathEventContextImpl pathEventContext = handback.context;
        if (pathEventContext.isInstallServices()) {
            if (attributeName.equals(PATH)) {
                pathManager.changePath(pathName, backup.getPath());
                pathManager.changePathServices(context, pathName, valueToRestore.asString());
            } else if (attributeName.equals(RELATIVE_TO)) {
                try {
                    pathManager.changeRelativePath(pathName, backup.getRelativeTo(), false);
                } catch (OperationFailedException e) {
                    //Should not happen since false passed in for the 'check' parameter
                    throw new RuntimeException(e);
                }
                pathManager.changeRelativePathServices(context, pathName, valueToRestore.isDefined() ?  valueToRestore.asString() : null);
            }
        } else {
            pathEventContext.revert();
        }
    }

    static class PathUpdate {
        private final PathEntry backup;
        private final PathEventContextImpl context;

        private PathUpdate(PathEntry backup, PathEventContextImpl context) {
            this.backup = backup;
            this.context = context;
        }
    }
}
