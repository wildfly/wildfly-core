/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.services.net;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.network.SocketBinding;
import org.jboss.dmr.ModelNode;

/**
 * Handler for changing the interface on a socket binding.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class BindingInterfaceHandler extends AbstractBindingWriteHandler {

    public static final BindingInterfaceHandler INSTANCE = new BindingInterfaceHandler();

    private BindingInterfaceHandler() {
    }

    @Override
    protected boolean requiresRestart() {
        return true;
    }

    @Override
    void handleRuntimeChange(OperationContext context, ModelNode operation, String attributeName, ModelNode attributeValue, SocketBinding binding) {
        // interface change always requires a restart
    }

    @Override
    void handleRuntimeRollback(OperationContext context, ModelNode operation, String attributeName, ModelNode previousValue, SocketBinding binding) {
        // interface change always requires a restart
    }
}
