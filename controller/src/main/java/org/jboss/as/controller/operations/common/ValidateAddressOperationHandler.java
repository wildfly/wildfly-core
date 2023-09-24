/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.common;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROBLEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.util.Collections;
import java.util.Iterator;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.Action.ActionEffect;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.AuthorizationResult.Decision;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.operations.validation.PathAddressValidator;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 *
 * @author Alexey Loubyansky
 */
public class ValidateAddressOperationHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = "validate-address";
    public static final ValidateAddressOperationHandler INSTANCE = new ValidateAddressOperationHandler();

    private static final AttributeDefinition VALUE_PARAM = SimpleAttributeDefinitionBuilder.create(VALUE, ModelType.OBJECT)
            .setValidator(PathAddressValidator.INSTANCE)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME, ControllerResolver.getResolver("global"))
        .addParameter(VALUE_PARAM)
        .setReplyParameters(
                SimpleAttributeDefinitionBuilder.create(VALID, ModelType.BOOLEAN).build(),
                SimpleAttributeDefinitionBuilder.create(PROBLEM, ModelType.STRING)
                    .setRequired(false)
                    .build()
                )
        .setReadOnly()
        .build();

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        ModelNode addr = VALUE_PARAM.validateOperation(operation);
        final PathAddress pathAddr = PathAddress.pathAddress(addr);
        Resource model = context.readResource(PathAddress.EMPTY_ADDRESS);
        final Iterator<PathElement> iterator = pathAddr.iterator();
        PathAddress current = PathAddress.EMPTY_ADDRESS;
        out: while(iterator.hasNext()) {
            final PathElement next = iterator.next();
            current = current.append(next);

            // Check if the registration is a proxy and dispatch directly
            final ImmutableManagementResourceRegistration registration = context.getResourceRegistration().getSubModel(current);

            if(registration != null && registration.isRemote()) {

                // If the target is a registered proxy return immediately
                if(! iterator.hasNext()) {
                    break out;
                }

                // Create the proxy op
                final PathAddress newAddress = pathAddr.subAddress(current.size());
                final ModelNode newOperation = operation.clone();
                newOperation.get(OP_ADDR).set(current.toModelNode());
                newOperation.get(VALUE).set(newAddress.toModelNode());

                // On the DC the host=master is not a proxy but the validate-address is registered at the root
                // Otherwise delegate to the proxy handler
                final OperationStepHandler proxyHandler = registration.getOperationHandler(PathAddress.EMPTY_ADDRESS, OPERATION_NAME);
                if(proxyHandler != null) {
                    context.addStep(newOperation, proxyHandler, OperationContext.Stage.MODEL, true);
                    return;
                }

            } else if (model.hasChild(next)) {
                model = model.getChild(next);
            } else {
                // Invalid
                context.getResult().get(VALID).set(false);
                context.getResult().get(PROBLEM).set(ControllerLogger.ROOT_LOGGER.childResourceNotFound(next));
                return;
            }
        }

        if (authorize(context, current, operation).getDecision() == Decision.DENY) {
            context.getResult().get(VALID).set(false);
            context.getResult().get(PROBLEM).set(ControllerLogger.ROOT_LOGGER.managementResourceNotFoundMessage(current));
        } else {
            context.getResult().get(VALID).set(true);
        }
    }

    private AuthorizationResult authorize(OperationContext context, PathAddress address, ModelNode operation) {
        ModelNode authOp = operation.clone();
        authOp.get(OP).set(READ_RESOURCE_OPERATION);
        authOp.get(OP_ADDR).set(address.toModelNode());
        return context.authorize(authOp, Collections.singleton(ActionEffect.ADDRESS));
    }
}
