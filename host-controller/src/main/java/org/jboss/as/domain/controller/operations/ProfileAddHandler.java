/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 */
public class ProfileAddHandler extends AbstractAddStepHandler {

    public static final ProfileAddHandler INSTANCE = new ProfileAddHandler();

    private ProfileAddHandler() {
    }

    protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {
        DomainModelIncludesValidator.addValidationStep(context, operation);
        super.populateModel(context, operation, resource);
    }

    protected boolean requiresRuntime(OperationContext context) {
        return false;
    }
}
