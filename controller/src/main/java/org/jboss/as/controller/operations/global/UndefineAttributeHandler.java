/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.global;

import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.NAME;
import static org.jboss.as.controller.operations.global.GlobalOperationAttributes.VALUE;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.dmr.ModelNode;

/**
 * The undefine-attribute handler, writing an undefined value for a single attribute.
 */
public class UndefineAttributeHandler extends WriteAttributeHandler {

    static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(ModelDescriptionConstants.UNDEFINE_ATTRIBUTE_OPERATION, ControllerResolver.getResolver("global"))
            .setParameters(NAME)
            .build();

    public static final OperationStepHandler INSTANCE = new UndefineAttributeHandler();

    @Override
    public void execute(final OperationContext context, final ModelNode original) throws OperationFailedException {
        final ModelNode operation = original.clone();
        operation.get(VALUE.getName()).set(new ModelNode());
        super.execute(context, operation);
    }

}
