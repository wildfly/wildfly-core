/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.common;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALIDATE_OPERATION;

import java.util.Collections;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProxyOperationAddressTranslator;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.Action.ActionEffect;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.AuthorizationResult.Decision;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.operations.validation.OperationValidator;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Validates an operation
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ValidateOperationHandler implements OperationStepHandler {

    public static final ValidateOperationHandler INSTANCE = new ValidateOperationHandler(false);
    public static final ValidateOperationHandler SLAVE_HC_INSTANCE = new ValidateOperationHandler(true);

    private static final SimpleAttributeDefinition VALUE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.VALUE, ModelType.OBJECT)
            .setRequired(true)
            .build();

    public static final OperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(VALIDATE_OPERATION, ControllerResolver.getResolver("global"))
            .setParameters(VALUE)
            .setReadOnly()
            .setRuntimeOnly()
            .build();

    public static final OperationDefinition DEFINITION_HIDDEN = new SimpleOperationDefinitionBuilder(VALIDATE_OPERATION, ControllerResolver.getResolver("global"))
            .setParameters(VALUE)
            .setReadOnly()
            .setRuntimeOnly()
            .withFlags(OperationEntry.Flag.HIDDEN) // can't be private, because the proxyReg != null case in execute results in a caller-type=user op being executed for this
            .build();


    private final boolean slave;

    private ValidateOperationHandler(boolean slave) {
        this.slave = slave;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        ModelNode op = VALUE.validateOperation(operation);
        PathAddress addr = PathAddress.pathAddress(op.get(OP_ADDR));
        if (slave) {
            op = op.clone();
            //Get rid of the initial host element
            if (addr.size() > 0 && addr.getElement(0).getKey().equals(HOST)) {
                addr = addr.subAddress(1);
                op.get(OP_ADDR).set(addr.toModelNode());
            }
        }


        ProxyOperationAddressTranslator translator = null;
        ImmutableManagementResourceRegistration proxyReg = null;
        PathAddress proxyAddr = PathAddress.EMPTY_ADDRESS;
        for (PathElement element : addr) {
            proxyAddr = proxyAddr.append(element);
            ImmutableManagementResourceRegistration reg = context.getResourceRegistration().getSubModel(proxyAddr);
            if (reg != null && reg.isRemote()) {
                translator = element.getKey().equals(SERVER) ? ProxyOperationAddressTranslator.SERVER : ProxyOperationAddressTranslator.HOST;
                proxyReg = reg;
                break;
            }
        }

        if (proxyReg != null) {
            ModelNode proxyOp = operation.clone();
            proxyOp.get(OP_ADDR).set(proxyAddr.toModelNode());
            proxyOp.get(VALUE.getName(), OP_ADDR).set(translator.translateAddress(addr).toModelNode());
            final ModelNode result = new ModelNode();

            context.addStep(result, proxyOp, proxyReg.getOperationHandler(PathAddress.EMPTY_ADDRESS, VALIDATE_OPERATION), Stage.MODEL, true);
            context.completeStep(new OperationContext.RollbackHandler() {

                @Override
                public void handleRollback(OperationContext context, ModelNode operation) {
                    context.getFailureDescription().set(result.get(FAILURE_DESCRIPTION));
                }
            });
        } else {
            try {
                if (authorize(context, op, operation).getDecision() == Decision.DENY) {
                    context.getFailureDescription().set(ControllerLogger.ROOT_LOGGER.managementResourceNotFoundMessage(addr));
                } else {
                    new OperationValidator(context, context.getResourceRegistration(), false, false, true).validateOperation(op);
                }
            } catch (IllegalArgumentException e) {
                context.getFailureDescription().set(e.getMessage());
            }
            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        }
    }


    private AuthorizationResult authorize(OperationContext context, ModelNode authOp, ModelNode opWithHeaders) {
        authOp.get(OPERATION_HEADERS).set(opWithHeaders.get(OPERATION_HEADERS));
        return context.authorize(authOp, Collections.singleton(ActionEffect.ADDRESS));
    }

}
