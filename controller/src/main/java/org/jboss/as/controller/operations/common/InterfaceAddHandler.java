/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.operations.common;


import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.interfaces.ParsedInterfaceCriteria;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.resource.InterfaceDefinition;
import org.jboss.dmr.ModelNode;

/**
 * Handler for the interface resource add operation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author Emanuel Muckenhuber
 */
public class InterfaceAddHandler extends AbstractAddStepHandler {

    private final boolean specified;

    /**
     * Create the InterfaceAddHandler
     */
    protected InterfaceAddHandler(boolean specified) {
        super();
        this.specified = specified;
    }

    protected void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {
        for (final AttributeDefinition definition : InterfaceDefinition.ROOT_ATTRIBUTES) {
            if(specified || operation.hasDefined(definition.getName())) {
                validateAndSet(definition, operation, model);
            }
        }
    }

    protected void validateAndSet(final AttributeDefinition definition, final ModelNode operation, final ModelNode subModel) throws OperationFailedException {
        final String attributeName = definition.getName();
        final boolean has = operation.has(attributeName);
        if(! has && definition.isRequired(operation)) {
            throw ControllerLogger.ROOT_LOGGER.required(attributeName);
        }
        if(has) {
            if(! definition.isAllowed(operation)) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalid(attributeName));
            }
            definition.validateAndSet(operation, subModel);
        } else {
            // create the undefined node
            subModel.get(definition.getName());
        }
    }

    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        String name = context.getCurrentAddressValue();
        ParsedInterfaceCriteria parsed = getCriteria(context, model);
        if (parsed.getFailureMessage() != null) {
            throw new OperationFailedException(parsed.getFailureMessage());
        }
        performRuntime(context, operation, model, name, parsed);
    }

    protected ParsedInterfaceCriteria getCriteria(OperationContext context, ModelNode operation) {
        return ParsedInterfaceCriteria.parse(operation, specified, context);
    }

    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model, String name, ParsedInterfaceCriteria criteria) {
    }


}
