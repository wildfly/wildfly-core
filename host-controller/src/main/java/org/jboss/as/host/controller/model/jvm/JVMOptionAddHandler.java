/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.host.controller.model.jvm;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

final class JVMOptionAddHandler implements OperationStepHandler {

    static final String OPERATION_NAME = "add-jvm-option";
    static final JVMOptionAddHandler INSTANCE = new JVMOptionAddHandler();

    // the attribute allows expressions that are resolved in JVMAddHandler upon server restart
    static final SimpleAttributeDefinition JVM_OPTION = SimpleAttributeDefinitionBuilder.create(JvmAttributes.JVM_OPTION, ModelType.STRING, false)
            .setValidator(new StringLengthValidator(1, false, true))
            .setAllowExpression(true)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, HostResolver.getResolver("jvm"))
        .addParameter(JVM_OPTION)
        .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.JVM)
        .build();


    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final Resource resource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
        final ModelNode model = resource.getModel();

        final ModelNode option = JVM_OPTION.validateOperation(operation);
        ModelNode jvmOptions = model.get(JvmAttributes.JVM_OPTIONS);
        if (jvmOptions.isDefined()) {
            for (ModelNode optionNode : jvmOptions.asList()) {
                if (optionNode.equals(option)) {
                    throw HostControllerLogger.ROOT_LOGGER.jvmOptionAlreadyExists(option.asString());
                }
            }
        }
        model.get(JvmAttributes.JVM_OPTIONS).add(option);
    }
}
