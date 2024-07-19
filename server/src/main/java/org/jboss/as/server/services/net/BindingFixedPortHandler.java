/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.services.net;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.network.SocketBinding;
import org.jboss.dmr.ModelNode;

/**
 * Handler for changing the fixed-port setting on a socket binding.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class BindingFixedPortHandler extends AbstractBindingWriteHandler {

    public static final BindingFixedPortHandler INSTANCE = new BindingFixedPortHandler();

    private BindingFixedPortHandler() {
    }

    @Override
    void handleRuntimeChange(OperationContext context, ModelNode operation, String attributeName, ModelNode attributeValue, SocketBinding binding) {
        binding.setFixedPort(attributeValue.asBoolean());
    }

    @Override
    void handleRuntimeRollback(OperationContext context, ModelNode operation, String attributeName, ModelNode previousValue, SocketBinding binding) {
        binding.setFixedPort(previousValue.asBoolean());
    }
}
