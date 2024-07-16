/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;

import org.jboss.as.controller.ModelOnlyAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Handler for the domain socket-binding-group resource's add operation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 *
 */
public class SocketBindingGroupAddHandler extends ModelOnlyAddStepHandler {

    public static final SocketBindingGroupAddHandler INSTANCE = new SocketBindingGroupAddHandler();

    private SocketBindingGroupAddHandler() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void populateModel(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        super.populateModel(context, operation, resource);

        //  We need to store the address value in the 'name' instead of using
        // ReadResourceNameOperationStepHandler to avoid picky legacy controller
        // model comparison failures
        resource.getModel().get(NAME).set(context.getCurrentAddressValue());

        DomainModelIncludesValidator.addValidationStep(context, operation);
    }
}
