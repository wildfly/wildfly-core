/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACTIVE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;

import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.domain.management._private.DomainManagementResolver;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.OperationStepHandler} that looks for and cancels a single operation that is in
 * execution status {@link org.jboss.as.controller.OperationContext.ExecutionStatus#AWAITING_STABILITY}
 * and has been executing in that status for longer than a specified {@code timeout} seconds.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 */
public class CancelNonProgressingOperationHandler implements OperationStepHandler {

    private static final AttributeDefinition STABILITY_TIMEOUT = SimpleAttributeDefinitionBuilder.create("timeout", ModelType.INT)
            .setRequired(false)
            .setDefaultValue(new ModelNode(15))
            .setValidator(new IntRangeValidator(0, true))
            .setMeasurementUnit(MeasurementUnit.SECONDS)
            .build();

    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("cancel-non-progressing-operation",
            DomainManagementResolver.getResolver(CORE, MANAGEMENT_OPERATIONS))
            .setReplyType(ModelType.STRING)
            .withFlag(OperationEntry.Flag.HOST_CONTROLLER_ONLY)
            .setRuntimeOnly()
            .build();

    static final OperationStepHandler INSTANCE = new CancelNonProgressingOperationHandler();

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final long timeout = TimeUnit.SECONDS.toNanos(STABILITY_TIMEOUT.resolveModelAttribute(context, operation).asLong());

        DomainManagementLogger.ROOT_LOGGER.debugf("Cancel of operation not progressing after [%d] ns requested", timeout);

        String blockingId = FindNonProgressingOperationHandler.findNonProgressingOp(context, timeout);

        if (blockingId != null) {

            final String toCancel = blockingId;
            PathAddress pa = PathAddress.pathAddress(operation.get(OP_ADDR));
            ModelNode op = Util.createEmptyOperation(CancelActiveOperationHandler.DEFINITION.getName(),
                    pa.append(PathElement.pathElement(ACTIVE_OPERATION, toCancel)));
            final ModelNode response = new ModelNode();
            context.addStep(response, op, CancelActiveOperationHandler.INSTANCE, OperationContext.Stage.MODEL, true);
            context.completeStep(new OperationContext.ResultHandler() {
                @Override
                public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                    if (response.hasDefined(RESULT) && response.get(RESULT).asBoolean()) {
                        context.getResult().set(toCancel);
                    }
                }
            });
        } else {
            throw DomainManagementLogger.ROOT_LOGGER.noNonProgressingOperationFound(TimeUnit.NANOSECONDS.toSeconds(timeout));
        }
    }
}
