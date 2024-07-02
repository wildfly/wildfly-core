/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.operations.global.EnhancedSyntaxSupport.containsEnhancedSyntax;
import static org.jboss.as.controller.operations.global.EnhancedSyntaxSupport.extractAttributeName;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
abstract class AbstractCollectionHandler implements OperationStepHandler {

    static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder("name", ModelType.STRING).setRequired(true).build();
    static final SimpleAttributeDefinition VALUE = GlobalOperationAttributes.VALUE;

    private final AttributeDefinition[] attributes;
    private final boolean requiredReadWriteAccess;

    AbstractCollectionHandler(AttributeDefinition... attributes) {
        this.attributes = attributes;
        requiredReadWriteAccess = true;
    }

    AbstractCollectionHandler(boolean requiredReadWrite, AttributeDefinition... attributes) {
        this.attributes = attributes;
        requiredReadWriteAccess = requiredReadWrite;
    }

    protected void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {
        NAME.validateAndSet(operation, model);
        for (AttributeDefinition attr : attributes) {
            if (attr == VALUE){//don't validate VALUE attribute WFCORE-826
                model.get(VALUE.getName()).set(operation.get(VALUE.getName()));
            }else {
                attr.validateAndSet(operation, model);
            }
        }
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        ModelNode operationModel = new ModelNode();
        populateModel(operation, operationModel);
        String attributeName = NAME.resolveModelAttribute(context, operationModel).asString();
        final ImmutableManagementResourceRegistration registry = context.getResourceRegistration();
        final boolean useEnhancedSyntax = containsEnhancedSyntax(attributeName, registry);
        String attributeExpression = attributeName;
        if (useEnhancedSyntax) {
            attributeName = extractAttributeName(attributeName);
        }
        final AttributeAccess attributeAccess = context.getResourceRegistration().getAttributeAccess(PathAddress.EMPTY_ADDRESS, attributeName);
        if (attributeAccess == null) {
            throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.unknownAttribute(attributeName));
        }
        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));

        final ModelNode readResponse = new ModelNode();

        // prepare write operation
        ModelNode writeOperation = Util.createOperation(WriteAttributeHandler.DEFINITION, address);
        writeOperation.get(NAME.getName()).set(useEnhancedSyntax ? attributeExpression : attributeName);
        ModelNode writeOperationValue = writeOperation.get(ModelDescriptionConstants.VALUE); // value will be set in modification step

        if (attributeAccess.getStorageType() == AttributeAccess.Storage.CONFIGURATION) {

            // Steps need to be performed before any other steps, so they are added in opposite order
            // with addFirst=true parameter.

            // 2. modify value and register writing step
            context.addStep((context1, operation1) -> {
                updateModel(context, operationModel, attributeAccess.getAttributeDefinition(), readResponse.get(RESULT));

                // add write step
                if (requiredReadWriteAccess) {
                    writeOperationValue.set(readResponse.get(RESULT));
                    context.addStep(writeOperation, WriteAttributeHandler.INSTANCE, OperationContext.Stage.MODEL, true);
                }
            }, OperationContext.Stage.MODEL, true);

            // 1. read current attribute value
            ModelNode readAttributeOperation = Util.getReadAttributeOperation(address, useEnhancedSyntax ? attributeExpression : attributeName, false);
            context.addStep(readResponse, readAttributeOperation, ReadAttributeHandler.INSTANCE, OperationContext.Stage.MODEL, true);
        } else {
            assert attributeAccess.getStorageType() == AttributeAccess.Storage.RUNTIME;

            // For Storage.RUNTIME attributes, attributes need to be registered with reader and writer step handlers,
            // which must postpone reading / writing to RUNTIME stage (by registering new RUNTIME steps which will
            // perform actual reading / writing).

            // Steps need to be performed before any other steps, so they are added in opposite order
            // with addFirst=true parameter.

            // 3. write modified value
            if (requiredReadWriteAccess) {
                context.addStep(readResponse, writeOperation, WriteAttributeHandler.INSTANCE, OperationContext.Stage.MODEL, true);
            }

            // 2. modify value
            context.addStep((context1, operation1) -> {
                context.addStep((context2, operation2) -> {
                    updateModel(context2, operationModel, attributeAccess.getAttributeDefinition(), readResponse.get(RESULT));
                    writeOperationValue.set(readResponse.get(RESULT));
                }, OperationContext.Stage.RUNTIME);
            }, OperationContext.Stage.MODEL, true);

            // 1. read current attribute value
            ModelNode readAttributeOperation = Util.getReadAttributeOperation(address, useEnhancedSyntax ? attributeExpression : attributeName);
            context.addStep(readResponse, readAttributeOperation, ReadAttributeHandler.INSTANCE, OperationContext.Stage.MODEL, true);
        }

        context.completeStep(new OperationContext.RollbackHandler() {
            @Override
            public void handleRollback(OperationContext context, ModelNode operation) {
                // If the read failed we *probably* have no failure description in our own response.
                // So propagate the read failure desc so the user has a clue what happened
                if (!context.hasFailureDescription() && readResponse.hasDefined(FAILURE_DESCRIPTION)) {
                    context.getFailureDescription().set(readResponse.get(FAILURE_DESCRIPTION));
                }
            }
        });
    }

    abstract void updateModel(final OperationContext context, ModelNode model, AttributeDefinition attributeDefinition, ModelNode attribute) throws OperationFailedException;
}
