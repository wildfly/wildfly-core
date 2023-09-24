/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.services.path;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.services.path.PathResourceDefinition.PATH_CAPABILITY;
import static org.jboss.as.controller.services.path.PathResourceDefinition.PATH_SPECIFIED;
import static org.jboss.as.controller.services.path.PathResourceDefinition.RELATIVE_TO;
import static org.jboss.as.controller.services.path.PathResourceDefinition.RELATIVE_TO_LOCAL;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.services.path.PathManager.Event;
import org.jboss.as.controller.services.path.PathManagerService.PathEventContextImpl;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceTarget;

/**
 * Handler for the path resource add operation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class PathAddHandler implements OperationStepHandler {  // TODO make this package protected

    public static final String OPERATION_NAME = ADD;

    public static ModelNode getAddPathOperation(PathAddress address, ModelNode path, ModelNode relativeTo) {
        ModelNode op = Util.createAddOperation(address);
        if (path.isDefined()) {
            op.get(PATH_SPECIFIED.getName()).set(path);
        }
        if (relativeTo.isDefined()) {
            op.get(RELATIVE_TO.getName()).set(relativeTo);
        }
        return op;
    }

    private final SimpleAttributeDefinition pathAttribute;
    private final PathManagerService pathManager;

    /**
     * Create the PathAddHandler
     *
     * @param pathManager   the path manager, or {@code null} if interaction with the path manager is not required
     *                      for the resource
     * @param pathAttribute the definition of the attribute to use to represent the portion of the path specification
     *                      that identifies the absolute path or portion of the path that is relative to the 'relative-to' path.
     *                      Cannot be {@code null}
     */
    PathAddHandler(final PathManagerService pathManager, final SimpleAttributeDefinition pathAttribute) {
        this.pathManager = pathManager;
        this.pathAttribute = pathAttribute;
    }

    static PathAddHandler createNamedInstance() {
        return new PathAddHandler(null, PathResourceDefinition.PATH_NAMED);
    }

    static PathAddHandler createSpecifiedInstance(final PathManagerService pathManager) {
        assert pathManager != null;
        return new PathAddHandler(pathManager, PathResourceDefinition.PATH_SPECIFIED);
    }

    static PathAddHandler createSpecifiedNoServicesInstance() {
        return new PathAddHandler(null, PathResourceDefinition.PATH_SPECIFIED);
    }


    /** {@inheritDoc */
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        final Resource resource = context.createResource(PathAddress.EMPTY_ADDRESS);

        final ModelNode model = resource.getModel();
        final String name = context.getCurrentAddressValue();
        pathAttribute.validateAndSet(operation, model);
        RELATIVE_TO_LOCAL.validateAndSet(operation, model);

        context.registerCapability(PATH_CAPABILITY.fromBaseCapability(context.getCurrentAddressValue()));
        RELATIVE_TO_LOCAL.addCapabilityRequirements(context, null, model.get(RELATIVE_TO.getName()));

        if (pathManager != null) {
            final String path = getPathValue(context, PATH_SPECIFIED, model);
            final String relativeTo = getPathValue(context, RELATIVE_TO, model);
            final PathEventContextImpl pathEventContext = pathManager.checkRestartRequired(context, name, Event.ADDED);

            if (pathEventContext.isInstallServices()) {
                //Add entry to the path manager
                pathManager.addPathEntry(name, path, relativeTo, false);
            }

            context.addStep(new OperationStepHandler() {
                public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                    final ServiceController<?> legacyService;
                    if (pathEventContext.isInstallServices()) {

                        //Add the legacy services
                        final ServiceTarget target = context.getServiceTarget();
                        if (relativeTo == null) {
                            legacyService = pathManager.addAbsolutePathService(target, name, path);
                        } else {
                            legacyService = pathManager.addRelativePathService(target, name, path, false, relativeTo);
                        }
                    } else {
                        legacyService = null;
                    }

                    context.completeStep(new OperationContext.RollbackHandler() {
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            pathManager.removePathService(context, name);
                            if (pathEventContext.isInstallServices()) {
                                if (legacyService != null) {
                                    context.removeService(legacyService);
                                }
                            } else {
                                pathEventContext.revert();
                            }
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);

            context.completeStep(new OperationContext.RollbackHandler() {
                public void handleRollback(OperationContext context, ModelNode operation) {
                    if (pathEventContext.isInstallServices()) {
                        try {
                            pathManager.removePathEntry(name, false);
                        } catch (OperationFailedException e) {
                            //Should not happen since 'false' passed in for the check parameter
                            throw new RuntimeException(e);
                        }
                    }
                }
            });
        } else {
            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }

    }

    static String getPathValue(OperationContext context, SimpleAttributeDefinition def, ModelNode model) throws OperationFailedException {
        final ModelNode resolved = def.resolveModelAttribute(context, model);
        return resolved.isDefined() ? resolved.asString() : null;
    }
}
