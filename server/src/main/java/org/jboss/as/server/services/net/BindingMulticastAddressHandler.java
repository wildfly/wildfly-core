/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.services.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.resource.AbstractSocketBindingResourceDefinition;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Handler for changing the interface on a socket binding.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class BindingMulticastAddressHandler extends AbstractBindingWriteHandler {

    public static final BindingMulticastAddressHandler INSTANCE = new BindingMulticastAddressHandler();

    private BindingMulticastAddressHandler() {
        super(AbstractSocketBindingResourceDefinition.MULTICAST_ADDRESS);
    }

    @Override
    void handleRuntimeChange(OperationContext context, ModelNode operation, String attributeName, ModelNode attributeValue, SocketBinding binding) throws OperationFailedException {
        final InetAddress address;
        if(attributeValue.isDefined()) {
            String addrString = attributeValue.asString();
            try {
                address = InetAddress.getByName(addrString);
            } catch (UnknownHostException e) {
                throw ServerLogger.ROOT_LOGGER.failedToResolveMulticastAddress(e, addrString);
            }
        } else {
            address = null;
        }
        binding.setMulticastAddress(address);
    }

    @Override
    void handleRuntimeRollback(OperationContext context, ModelNode operation, String attributeName, ModelNode attributeValue, SocketBinding binding) {
        final InetAddress address;
        if(attributeValue.isDefined()) {
            String addrString = attributeValue.asString();
            try {
                address = InetAddress.getByName(addrString);
            } catch (UnknownHostException e) {
                throw ServerLogger.ROOT_LOGGER.failedToResolveMulticastAddressForRollback(e, addrString);
            }
        } else {
            address = null;
        }
        binding.setMulticastAddress(address);
    }
}
