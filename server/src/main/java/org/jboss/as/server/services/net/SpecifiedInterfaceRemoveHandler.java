/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.services.net;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.dmr.ModelNode;

/**
 * Handler for removing a fully-specified interface.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class SpecifiedInterfaceRemoveHandler extends AbstractRemoveStepHandler {

    public static final SpecifiedInterfaceRemoveHandler INSTANCE = new SpecifiedInterfaceRemoveHandler();

    protected SpecifiedInterfaceRemoveHandler() {
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return context.getProcessType().isServer();
    }

    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) {
        String name = context.getCurrentAddressValue();
        context.removeService(InterfaceResourceDefinition.INTERFACE_CAPABILITY.getCapabilityServiceName(name));
    }

    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) {
        // TODO: Re-Add Services
    }
}
