/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.operations.validation.ParametersValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.platform.mbean.logging.PlatformMBeanLogger;
import org.jboss.dmr.ModelNode;

/**
 * Base class for handlers for reading and writing platform mbean attributes.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
abstract class AbstractPlatformMBeanAttributeHandler implements OperationStepHandler {

    private final ParametersValidator readAttributeValidator = new ParametersValidator();
    final ParametersValidator writeAttributeValidator = new ParametersValidator();

    protected AbstractPlatformMBeanAttributeHandler() {
        readAttributeValidator.registerValidator(NAME, new StringLengthValidator(1));
        writeAttributeValidator.registerValidator(NAME, new StringLengthValidator(1));
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        String op = operation.require(OP).asString();
        if (READ_ATTRIBUTE_OPERATION.equals(op)) {
            readAttributeValidator.validate(operation);
            context.addStep(this::executeReadAttribute, OperationContext.Stage.RUNTIME);
        } else if (WRITE_ATTRIBUTE_OPERATION.equals(op)) {
            writeAttributeValidator.validate(operation);
            context.addStep(this::executeWriteAttribute, OperationContext.Stage.RUNTIME);
        }

        context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
    }

    protected abstract void executeReadAttribute (OperationContext context, ModelNode operation) throws OperationFailedException;

    protected abstract void executeWriteAttribute (OperationContext context, ModelNode operation) throws OperationFailedException;

    protected static OperationFailedException unknownAttribute(final ModelNode operation) {
        return PlatformMBeanLogger.ROOT_LOGGER.unknownAttribute(operation.require(NAME).asString());
    }
}
