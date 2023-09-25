/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACTIVE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;

import java.util.EnumSet;

import org.jboss.as.controller.Cancellable;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.OperationStepHandler} to cancel an
 * {@link org.jboss.as.domain.management.controller.ActiveOperationResourceDefinition active operation}.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class CancelActiveOperationHandler implements OperationStepHandler {

    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("cancel",
            DomainManagementResolver.getResolver(CORE, MANAGEMENT_OPERATIONS, ACTIVE_OPERATION))
            .setReplyType(ModelType.BOOLEAN)
            .withFlag(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setRuntimeOnly()
            .build();

    static final OperationStepHandler INSTANCE = new CancelActiveOperationHandler();

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        DomainManagementLogger.ROOT_LOGGER.debugf("Cancel of %s requested", PathAddress.pathAddress(operation.get(OP_ADDR)).getLastElement().getValue());
        AuthorizationResult authorizationResult = context.authorize(operation, EnumSet.of(Action.ActionEffect.WRITE_RUNTIME));
        if (authorizationResult.getDecision() == AuthorizationResult.Decision.DENY) {
            throw ControllerLogger.ACCESS_LOGGER.unauthorized(operation.get(OP).asString(),
                    PathAddress.pathAddress(operation.get(OP_ADDR)), authorizationResult.getExplanation());
        }
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                boolean cancelled = false;
                try {
                    Cancellable cancellable = Cancellable.class.cast(context.readResource(PathAddress.EMPTY_ADDRESS));
                    DomainManagementLogger.ROOT_LOGGER.debugf("Cancelling %s", cancellable);
                    cancelled = cancellable.cancel();
                } catch (Resource.NoSuchResourceException nsre) {
                    // resource is gone; return 'false'
                }
                context.getResult().set(cancelled);

                context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
