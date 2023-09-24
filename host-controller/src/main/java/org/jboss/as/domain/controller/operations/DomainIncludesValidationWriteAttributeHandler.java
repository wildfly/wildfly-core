/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.domain.controller.operations.coordination.ServerOperationResolver;
import org.jboss.dmr.ModelNode;

/**
 * Write attribute handler to trigger validation when something in the domain model involving includes has changed
 *
 * @author Kabir Khan
 */
public class DomainIncludesValidationWriteAttributeHandler extends ModelOnlyWriteAttributeHandler {

    public DomainIncludesValidationWriteAttributeHandler(AttributeDefinition... attributeDefinitions) {
        super(attributeDefinitions);
    }

    @Override
    protected void finishModelStage(OperationContext context, ModelNode operation, String attributeName, ModelNode newValue,
                                    ModelNode oldValue, Resource model) throws OperationFailedException {
        super.finishModelStage(context, operation, attributeName, newValue, oldValue, model);
        if (newValue.equals(oldValue)) {
            //Set an attachment to avoid propagation to the servers, we don't want them to go into restart-required if nothing changed
            ServerOperationResolver.addToDontPropagateToServersAttachment(context, operation);
        }

        DomainModelIncludesValidator.addValidationStep(context, operation);
    }

}
