/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.operation;

import java.util.Optional;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.operations.global.ReadAttributeHandler;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.wildfly.subsystem.resource.AttributeTranslation;

/**
 * A {@value ModelDescriptionConstants#READ_ATTRIBUTE_OPERATION} operation handler that translates a value from another attribute
 * @author Paul Ferraro
 */
public class ReadAttributeTranslationOperationStepHandler implements OperationStepHandler {

    private final AttributeTranslation translation;

    public ReadAttributeTranslationOperationStepHandler(AttributeTranslation translation) {
        this.translation = translation;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        PathAddress currentAddress = context.getCurrentAddress();
        PathAddress targetAddress = this.translation.getPathAddressTranslator().apply(currentAddress);
        AttributeDefinition targetAttribute = this.translation.getTargetAttribute();
        ModelNode targetOperation = Util.getReadAttributeOperation(targetAddress, targetAttribute.getName());
        if (operation.hasDefined(ModelDescriptionConstants.INCLUDE_DEFAULTS)) {
            targetOperation.get(ModelDescriptionConstants.INCLUDE_DEFAULTS).set(operation.get(ModelDescriptionConstants.INCLUDE_DEFAULTS));
        }
        ImmutableManagementResourceRegistration registration = (currentAddress == targetAddress) ? context.getResourceRegistration() : context.getRootResourceRegistration().getSubModel(targetAddress);
        if (registration == null) {
            throw new OperationFailedException(ControllerLogger.MGMT_OP_LOGGER.noSuchResourceType(targetAddress));
        }
        OperationStepHandler readAttributeHandler = Optional.ofNullable(registration.getAttributeAccess(PathAddress.EMPTY_ADDRESS, targetAttribute.getName()).getReadHandler()).orElse(ReadAttributeHandler.RESOLVE_INSTANCE);
        OperationStepHandler readTranslatedAttributeHandler = new ReadTranslatedAttributeOperationStepHandler(readAttributeHandler, this.translation.getReadAttributeOperationTranslator());
        // If targetOperation applies to the current resource, we can execute in the current step
        if (targetAddress == currentAddress) {
            readTranslatedAttributeHandler.execute(context, targetOperation);
        } else {
            if (registration.isRuntimeOnly()) {
                try {
                    context.readResourceFromRoot(targetAddress, false);
                } catch (Resource.NoSuchResourceException ignore) {
                    // If the target runtime resource does not exist return UNDEFINED
                    return;
                }
            }
            context.addStep(targetOperation, readTranslatedAttributeHandler, context.getCurrentStage(), true);
        }
    }

    private static class ReadTranslatedAttributeOperationStepHandler implements OperationStepHandler {
        private final OperationStepHandler readHandler;
        private final AttributeTranslation.AttributeValueTranslator translator;

        ReadTranslatedAttributeOperationStepHandler(OperationStepHandler readHandler, AttributeTranslation.AttributeValueTranslator translator) {
            this.readHandler = readHandler;
            this.translator = translator;
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            this.readHandler.execute(context, operation);
            ModelNode result = context.getResult();
            result.set(this.translator.translate(context, result));
        }
    }
}
