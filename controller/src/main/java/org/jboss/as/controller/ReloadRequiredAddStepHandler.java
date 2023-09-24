/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.Collection;

import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * An handler for an 'add' operation that does nothing in the
 * {@link org.jboss.as.controller.OperationContext.Stage#RUNTIME runtime stage} but put the process in
 * reload-required state. It does nothing at all in the runtime stage during boot.
 * <p>
 * Use case for this handler is for resources that only represent configuration data of their parent.
 * During boot in the runtime stage the parent reads the child model and configures its services
 * accordingly. Thereafter any change to the child model should put the process in reload-required.
 * <p>
 * The {@link org.jboss.as.controller.RestartParentResourceAddHandler} performs a similar function, but
 * allows restart of the parent resource.
 *
 * @see org.jboss.as.controller.RestartParentResourceAddHandler
 *
 * @author Brian Stansberry
 */
public class ReloadRequiredAddStepHandler extends AbstractAddStepHandler {

    public ReloadRequiredAddStepHandler(AttributeDefinition... attributes) {
        super(attributes);
    }

    public ReloadRequiredAddStepHandler(Collection<AttributeDefinition> attributes) {
        super(attributes);
    }

    public ReloadRequiredAddStepHandler(Parameters parameters) {
        super(parameters);
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return !context.isBooting() && super.requiresRuntime(context);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) {
        context.reloadRequired();
    }

    @Override
    protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {
        context.revertReloadRequired();
    }
}
