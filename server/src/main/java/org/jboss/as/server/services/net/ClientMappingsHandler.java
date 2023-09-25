/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.services.net;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.resource.AbstractSocketBindingResourceDefinition;
import org.jboss.as.network.SocketBinding;
import org.jboss.dmr.ModelNode;

/**
 * Handler for changing the client mappings on a socket binding.
 *
 * @author Jason T. Greene
 */
public class ClientMappingsHandler extends AbstractBindingWriteHandler {

    public static final ClientMappingsHandler INSTANCE = new ClientMappingsHandler();

    private ClientMappingsHandler() {
        super(AbstractSocketBindingResourceDefinition.CLIENT_MAPPINGS);
    }

    @Override
    void handleRuntimeChange(OperationContext context, ModelNode operation, String attributeName, ModelNode attributeValue, SocketBinding binding) throws OperationFailedException {
         binding.setClientMappings(BindingAddHandler.parseClientMappings(context, attributeValue));
    }

    @Override
    void handleRuntimeRollback(OperationContext context, ModelNode operation, String attributeName, ModelNode attributeValue, SocketBinding binding) {
        try {
            binding.setClientMappings(BindingAddHandler.parseClientMappings(context, attributeValue));
        } catch (OperationFailedException e) {
            throw ControllerLogger.ROOT_LOGGER.failedToRecoverServices(e);
        }
    }
}
