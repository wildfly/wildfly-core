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
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

final class JVMOptionRemoveHandler implements OperationStepHandler {

    static final String OPERATION_NAME = "remove-jvm-option";
    static final JVMOptionRemoveHandler INSTANCE = new JVMOptionRemoveHandler();

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

        // the attribute allow expressions but it is *not* resolved. This enables to remove a jvm option
        // which was added with an expression. If the expression was resolved it would not be found in the JVM_OPTIONS list
        final ModelNode option = JVM_OPTION.validateOperation(operation);
        if (model.hasDefined(JvmAttributes.JVM_OPTIONS)) {
            final ModelNode values = model.get(JvmAttributes.JVM_OPTIONS).clone();
            model.get(JvmAttributes.JVM_OPTIONS).setEmptyList();

            for (ModelNode value : values.asList()) {
                if (!value.equals(option)) {
                    model.get(JvmAttributes.JVM_OPTIONS).add(value);
                }
            }
        }
    }
}
