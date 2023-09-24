/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.management.client.content;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;

/**
 * Add handler for a resource that represents a named bit of re-usable DMR.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ManagedDMRContentAddHandler extends AbstractAddStepHandler {

    public ManagedDMRContentAddHandler(final AttributeDefinition contentAttribute) {
        super(contentAttribute);

    }


    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        PathElement pe = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR)).getLastElement();

        ModelNode model = new ModelNode();
        populateModel(operation, model);

        // Create and add the specialized resource type we use for a managed dmr content resource
        ManagedDMRContentResource resource = new ManagedDMRContentResource(pe);
        context.addResource(PathAddress.EMPTY_ADDRESS, resource);
        // IMPORTANT: Use writeModel, as this is what causes the content to be flushed to the content repo!
        resource.writeModel(model);
    }
}
