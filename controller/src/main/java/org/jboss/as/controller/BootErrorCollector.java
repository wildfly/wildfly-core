/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import java.util.Set;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED_SERVICES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MISSING_TRANSITIVE_DEPENDENCY_PROBLEMS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.POSSIBLE_CAUSES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICES_MISSING_DEPENDENCIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVICES_MISSING_TRANSITIVE_DEPENDENCIES;

import java.util.HashSet;
import org.jboss.as.controller.access.management.AuthorizedAddress;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Collects boot errors.
 * @author <a href="mailto:ehugonne@redhat.com">Emmanuel Hugonnet</a> (c) 2014 Red Hat, inc.
 */
public class BootErrorCollector {
    private static final String COMPLETE_OP = "real-operation";
    private final ModelNode errors;
    private final OperationStepHandler listBootErrorsHandler;

    public BootErrorCollector() {
        errors = new ModelNode();
        errors.setEmptyList();
        listBootErrorsHandler = new ListBootErrorsHandler(this);
    }

    void addFailureDescription(final ModelNode operation, final ModelNode failureDescription) {
        assert operation != null;
        assert failureDescription != null;

        ModelNode error = new ModelNode();
        // for security reasons failure.get(FAILED).set(operation.clone());
        ModelNode failedOperation = error.get(FAILED_OPERATION);
        failedOperation.get(OP).set(operation.get(OP));
        ModelNode opAddr = operation.get(OP_ADDR);
        if (!opAddr.isDefined()) {
            opAddr.setEmptyList();
        }
        failedOperation.get(OP_ADDR).set(opAddr);

        error.get(FAILURE_DESCRIPTION).set(failureDescription.asString());
        ModelNode report = ServiceVerificationHelper.extractFailedServicesDescription(failureDescription);
        if (report != null) {
            error.get(FAILED_SERVICES).set(report);
        }
        report = ServiceVerificationHelper.extractMissingServicesDescription(failureDescription);
        if (report != null) {
            error.get(SERVICES_MISSING_DEPENDENCIES).set(report);
        }
        report = ServiceVerificationHelper.extractTransitiveDependencyProblemDescription(failureDescription);
        if (report != null) {
            error.get(MISSING_TRANSITIVE_DEPENDENCY_PROBLEMS).set(report);
        }
        error.get(COMPLETE_OP).set(operation);
        synchronized (errors) {
            errors.add(error);
        }
    }

    ModelNode getErrors() {
        synchronized (errors) {
            return errors.clone();
        }
    }

    public OperationStepHandler getReadBootErrorsHandler() {
        return this.listBootErrorsHandler;
    }

    public static class ListBootErrorsHandler implements OperationStepHandler {

        private static final String OPERATION_NAME = "read-boot-errors";
        private final BootErrorCollector errors;

        private static final AttributeDefinition OP_DEFINITION = ObjectTypeAttributeDefinition.Builder.of(FAILED_OPERATION,
                    SimpleAttributeDefinitionBuilder.create(OP, ModelType.STRING, false).build(),
                    SimpleListAttributeDefinition.Builder.of(OP_ADDR,
                            SimpleAttributeDefinitionBuilder.create("element", ModelType.PROPERTY, false).build())
                            .build())
                .setRequired(true)
                .build();

        private static final AttributeDefinition FAILURE_MESSAGE = SimpleAttributeDefinitionBuilder.create(FAILURE_DESCRIPTION, ModelType.STRING, false).build();

        private static final AttributeDefinition FAILED_SVC_AD = SimpleListAttributeDefinition.Builder.of(FAILED_SERVICES,
                SimpleAttributeDefinitionBuilder.create("element", ModelType.STRING, false).build())
                .setRequired(false)
                .build();

        private static final AttributeDefinition MISSING_DEPS_AD = SimpleListAttributeDefinition.Builder.of(SERVICES_MISSING_DEPENDENCIES,
                SimpleAttributeDefinitionBuilder.create("element", ModelType.STRING, false).build())
                .setRequired(false)
                .build();

        private static final AttributeDefinition AFFECTED_AD = SimpleListAttributeDefinition.Builder.of(SERVICES_MISSING_TRANSITIVE_DEPENDENCIES,
                    SimpleAttributeDefinitionBuilder.create("element", ModelType.STRING, false).build())
                .build();

        private static final AttributeDefinition CAUSE_AD = SimpleListAttributeDefinition.Builder.of(POSSIBLE_CAUSES,
                SimpleAttributeDefinitionBuilder.create("element", ModelType.STRING, false).build())
                .build();

        private static final AttributeDefinition TRANSITIVE_AD = ObjectTypeAttributeDefinition.Builder.of(MISSING_TRANSITIVE_DEPENDENCY_PROBLEMS,
                    AFFECTED_AD, CAUSE_AD)
                .setRequired(false)
                .build();

        public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME,
                ControllerResolver.getResolver("errors"))
                .setReadOnly()
                .setRuntimeOnly()
                .setReplyType(ModelType.LIST)
                .setReplyParameters(OP_DEFINITION, FAILURE_MESSAGE, FAILED_SVC_AD, MISSING_DEPS_AD, TRANSITIVE_AD).build();

        ListBootErrorsHandler(final BootErrorCollector errors) {
            this.errors = errors;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

                    ModelNode bootErrors = new ModelNode().setEmptyList();
                    ModelNode errorsNode = errors.getErrors();
                    for (ModelNode bootError : errorsNode.asList()) {
                        secureOperationAddress(context, bootError);
                        bootErrors.add(bootError);
                    }
                    context.getResult().set(bootErrors);
                }
            }, OperationContext.Stage.RUNTIME);
        }

        private void secureOperationAddress(OperationContext context, ModelNode bootError) throws OperationFailedException {
            if (bootError.hasDefined(FAILED_OPERATION)) {
                ModelNode failedOperation = bootError.get(FAILED_OPERATION);
                ModelNode address = failedOperation.get(OP_ADDR);
                ModelNode fakeOperation = new ModelNode();
                fakeOperation.get(OP).set(READ_RESOURCE_OPERATION);
                fakeOperation.get(OP_ADDR).set(address);
                AuthorizedAddress authorizedAddress = AuthorizedAddress.authorizeAddress(context, fakeOperation);
                if(authorizedAddress.isElided()) {
                    failedOperation.get(OP_ADDR).set(authorizedAddress.getAddress());
                }
                if(bootError.has(FAILURE_DESCRIPTION) && !canReadFailureDescription(context, bootError)) {
                    bootError.get(FAILURE_DESCRIPTION).set(new ModelNode());
                }
            }
            bootError.remove(COMPLETE_OP);
        }

        private boolean canReadFailureDescription(OperationContext context, ModelNode bootError) {
            ModelNode completeOPeration = bootError.get(COMPLETE_OP);
            OperationEntry operationEntry = context.getRootResourceRegistration().getOperationEntry(
                    PathAddress.pathAddress(completeOPeration.get(OP_ADDR)), completeOPeration.get(OP).asString());
            Set<Action.ActionEffect> effects = getEffects(operationEntry);
            return context.authorize(bootError.get(COMPLETE_OP), effects).getDecision() == AuthorizationResult.Decision.PERMIT;
        }

        Set<Action.ActionEffect> getEffects(OperationEntry operationEntry) {
            Set<Action.ActionEffect> effects = new HashSet<>(5);
            effects.add(Action.ActionEffect.ADDRESS);
            if(operationEntry != null) {
                effects.add(Action.ActionEffect.READ_RUNTIME);
                if (!operationEntry.getFlags().contains(OperationEntry.Flag.RUNTIME_ONLY)) {
                    effects.add(Action.ActionEffect.READ_CONFIG);
                }
                if(!operationEntry.getFlags().contains(OperationEntry.Flag.READ_ONLY)) {
                    effects.add(Action.ActionEffect.WRITE_RUNTIME);
                    if(!operationEntry.getFlags().contains(OperationEntry.Flag.RUNTIME_ONLY)) {
                        effects.add(Action.ActionEffect.WRITE_CONFIG);
                    }
                }
            }
            return effects;
        }
    }
}
