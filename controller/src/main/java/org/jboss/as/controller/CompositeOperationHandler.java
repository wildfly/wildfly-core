/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CALLER_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLED_BACK;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USER;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.MultistepUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Handler for the "composite" operation; i.e. one that includes one or more child operations
 * as steps.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class CompositeOperationHandler implements OperationStepHandler {

    /** Key that domain mode handlers can use to communicate that domain-specific execution has occurred. */
    public static final OperationContext.AttachmentKey<Boolean> DOMAIN_EXECUTION_KEY = OperationContext.AttachmentKey.create(Boolean.class);

    public static final CompositeOperationHandler INSTANCE = new CompositeOperationHandler();
    public static final String NAME = ModelDescriptionConstants.COMPOSITE;

    /** Gets the failure message used for reporting a rollback with no failure message in a step */
    public static String getUnexplainedFailureMessage() {
        return ControllerLogger.ROOT_LOGGER.compositeOperationRolledBack();
    }

    private static final AttributeDefinition STEPS = new PrimitiveListAttributeDefinition.Builder(ModelDescriptionConstants.STEPS, ModelType.OBJECT)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(NAME, ControllerResolver.getResolver("root"))
        .addParameter(STEPS)
        .setReplyType(ModelType.OBJECT)
        .setReplyValueType(ModelType.OBJECT)
        .build();

    public static final OperationDefinition INTERNAL_DEFINITION = new SimpleOperationDefinitionBuilder(NAME, ControllerResolver.getResolver("root"))
            .addParameter(STEPS)
            .setReplyType(ModelType.OBJECT)
            .setPrivateEntry()  // even if we eventually remove this from the non-internal definition, keep it here
            //.setForceDefaultDescriptionProvider()// this one we don't display description even if operation is private
            .build();

    protected CompositeOperationHandler() {
    }

    public final void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        STEPS.validateOperation(operation);

        final ModelNode responseMap = context.getResult().setEmptyObject();

        // Add a step to the OC for each element in the "steps" param.
        final List<ModelNode> list = operation.get(ModelDescriptionConstants.STEPS).asList();
        Map<String, ModelNode> operationMap = new LinkedHashMap<>();
        final Map<String, ModelNode> addedResponses = new LinkedHashMap<>();
        final int size = list.size();
        for (int i = 0; i < size; i++) {
            String stepName = "step-" + (i+1);

            operationMap.put(stepName, list.get(i));

            // This makes the result steps appear in the correct order
            ModelNode stepResp = responseMap.get(stepName);
            addedResponses.put(stepName, stepResp);
        }

        boolean adjustStepAddresses = context.getCurrentAddress().size() > 0;
        boolean rejectPrivateSteps = operation.hasDefined(OPERATION_HEADERS, CALLER_TYPE) && USER.equals(operation.get(OPERATION_HEADERS, CALLER_TYPE).asString());
        MultistepUtil.recordOperationSteps(context, operationMap, addedResponses,
                getOperationHandlerResolver(), adjustStepAddresses, rejectPrivateSteps);

        context.completeStep(new OperationContext.ResultHandler() {
            @Override
            public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {

                // don't override useful failure information in the domain
                // or any existing failure message
                boolean needFailureMessage = resultAction == OperationContext.ResultAction.ROLLBACK
                        && context.getAttachment(DOMAIN_EXECUTION_KEY) == null && !context.hasFailureDescription();

                final ModelNode failureMsg = needFailureMessage ? new ModelNode() : null;
                for (int i = 0; i < size; i++) {
                    String stepName = "step-" + (i+1);
                    ModelNode stepResponse = responseMap.get(stepName);
                    if (needFailureMessage && stepResponse.hasDefined(FAILURE_DESCRIPTION)) {
                        failureMsg.get(ControllerLogger.ROOT_LOGGER.compositeOperationFailed(), ControllerLogger.ROOT_LOGGER.operation(stepName)).set(stepResponse.get(FAILURE_DESCRIPTION));
                    }
                    // Clean out any cruft rolled-back nodes
                    if (stepResponse.has(ROLLED_BACK) && !stepResponse.hasDefined(ROLLED_BACK)) {
                        stepResponse.remove(ROLLED_BACK);
                    }
                }
                if (needFailureMessage) {
                    if (!failureMsg.isDefined()) {
                        failureMsg.set(getUnexplainedFailureMessage());
                    }
                    context.getFailureDescription().set(failureMsg);
                }
            }
        });
    }

    protected MultistepUtil.OperationHandlerResolver getOperationHandlerResolver() {
        return MultistepUtil.OperationHandlerResolver.DEFAULT;
    }
}
