/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.services.net;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.network.SocketBinding;
import org.jboss.dmr.ModelNode;

/**
 * Handler for changing the port on a socket binding.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class BindingPortHandler extends AbstractBindingWriteHandler {

    public static final BindingPortHandler INSTANCE = new BindingPortHandler();

    private BindingPortHandler() {
    }

    @Override
    void handleRuntimeChange(OperationContext context, ModelNode operation, String attributeName, ModelNode attributeValue, SocketBinding binding) throws OperationFailedException {
        binding.setPort(attributeValue.asInt());
    }

    @Override
    void handleRuntimeRollback(OperationContext context, ModelNode operation, String attributeName, ModelNode attributeValue, SocketBinding binding) {
        binding.setPort(attributeValue.asInt());
    }
}
