/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.controller.operations.common;


import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.capability.RuntimeCapability;
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
    protected InterfaceAddHandler(boolean specified, RuntimeCapability capability) {
        super(capability);
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
        ParsedInterfaceCriteria parsed = getCriteria(context, operation);
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
