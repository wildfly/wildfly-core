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
 * Handler for changing the multicast-port on a socket binding.
 *
 * @author Emanuel Muckenhuber
 */
public class BindingMulticastPortHandler extends AbstractBindingWriteHandler {

    public static final BindingMulticastPortHandler INSTANCE = new BindingMulticastPortHandler();

    private BindingMulticastPortHandler() {
    }

    @Override
    void handleRuntimeChange(OperationContext context, ModelNode operation, String attributeName, ModelNode attributeValue, SocketBinding binding) throws OperationFailedException {
        binding.setMulticastPort(attributeValue.asInt());
    }

    @Override
    void handleRuntimeRollback(OperationContext context, ModelNode operation, String attributeName, ModelNode attributeValue, SocketBinding binding) {
        binding.setMulticastPort(attributeValue.asInt());
    }
}
