/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.services.path;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;
import static org.jboss.as.controller.services.path.PathResourceDefinition.PATH_CAPABILITY;
import static org.jboss.as.controller.services.path.PathResourceDefinition.PATH_SPECIFIED;
import static org.jboss.as.controller.services.path.PathResourceDefinition.READ_ONLY;
import static org.jboss.as.controller.services.path.PathResourceDefinition.RELATIVE_TO;
import static org.jboss.as.controller.services.path.PathResourceDefinition.RELATIVE_TO_LOCAL;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.services.path.PathManager.Event;
import org.jboss.as.controller.services.path.PathManagerService.PathEventContextImpl;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceTarget;

/**
 * Handler for the path resource remove operation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class PathRemoveHandler implements OperationStepHandler { // TODO make this package protected

    public static final String OPERATION_NAME = REMOVE;

    private final PathManagerService pathManager;

    /**
     * Create the PathRemoveHandler
     *
     * @param pathManager the path manager, or {@code null} if interaction with the path manager is not required
     *                    for the resource
     */
    PathRemoveHandler(final PathManagerService pathManager) {
        this.pathManager = pathManager;
    }

    static PathRemoveHandler createNamedInstance() {
        return new PathRemoveHandler(null);
    }

    static PathRemoveHandler createSpecifiedInstance(final PathManagerService pathManager) {
        assert pathManager != null;
        return new PathRemoveHandler(pathManager);
    }

    static PathRemoveHandler createSpecifiedNoServicesInstance() {
        return new PathRemoveHandler(null);
    }

    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String name = context.getCurrentAddressValue();

        final ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();

        if (model.get(READ_ONLY.getName()).asBoolean(false)) {
            throw ControllerLogger.ROOT_LOGGER.cannotRemoveReadOnlyPath(name);
        }

        context.removeResource(PathAddress.EMPTY_ADDRESS);

        context.deregisterCapability(PATH_CAPABILITY.getDynamicName(context.getCurrentAddressValue()));
        RELATIVE_TO_LOCAL.removeCapabilityRequirements(context, null, model.get(RELATIVE_TO.getName()));

        if (pathManager != null) {
            final PathEventContextImpl pathEventContext = pathManager.checkRestartRequired(context, name, Event.REMOVED);

            // Capture the existing values to restore the PathEntry and services in case of rollback
            final String path;
            final String relativeTo;

            if (pathEventContext.isInstallServices()) {
                pathManager.removePathEntry(name, true);
                path = PathAddHandler.getPathValue(context, PATH_SPECIFIED, model);
                relativeTo = PathAddHandler.getPathValue(context, RELATIVE_TO, model);
            } else {
                path = relativeTo = null;
            }

            context.addStep(new OperationStepHandler() {
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    if (pathEventContext.isInstallServices()) {
                        pathManager.removePathService(context, name);
                    }

                    context.completeStep(new OperationContext.RollbackHandler() {
                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            try {
                                if (pathEventContext.isInstallServices()) {
                                    final ServiceTarget target = context.getServiceTarget();
                                    if (relativeTo == null) {
                                        pathManager.addAbsolutePathService(target, name, path);
                                    } else {
                                        pathManager.addRelativePathService(target, name, path, false, relativeTo);
                                    }
                                } else {
                                    context.revertRestartRequired();
                                }


                            } catch (Exception e) {
                                MGMT_OP_LOGGER.errorRevertingOperation(e, getClass().getSimpleName(),
                                    operation.require(ModelDescriptionConstants.OP).asString(),
                                    PathAddress.pathAddress(operation.get(ModelDescriptionConstants.OP_ADDR)));
                            }
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);

            context.completeStep(new OperationContext.RollbackHandler() {
                @Override
                public void handleRollback(OperationContext context, ModelNode operation) {
                    if (pathEventContext.isInstallServices()) {
                        // Re-add entry to the path manager
                        pathManager.addPathEntry(name, path, relativeTo, false);
                    }
                }
            });
        }
    }
}
