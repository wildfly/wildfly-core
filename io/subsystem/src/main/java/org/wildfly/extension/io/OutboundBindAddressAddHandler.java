/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import static org.wildfly.extension.io.OutboundBindAddressUtils.getBindAddress;
import static org.wildfly.extension.io.OutboundBindAddressUtils.getCidrAddress;
import static org.wildfly.extension.io.OutboundBindAddressUtils.getWorkerService;

import java.net.InetSocketAddress;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.wildfly.common.net.CidrAddress;
import org.wildfly.common.net.CidrAddressTable;
import org.wildfly.common.net.Inet;
import org.wildfly.extension.io.logging.IOLogger;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class OutboundBindAddressAddHandler extends AbstractAddStepHandler {

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {
        final CidrAddressTable<InetSocketAddress> bindingsTable = getWorkerService(context).getBindingsTable();
        if (bindingsTable != null) {
            final CidrAddress cidrAddress = getCidrAddress(operation, context);
            final InetSocketAddress bindAddress = getBindAddress(operation, context);
            final InetSocketAddress existing = bindingsTable.putIfAbsent(cidrAddress, bindAddress);
            if (existing != null) {
                throw IOLogger.ROOT_LOGGER.unexpectedBindAddressConflict(context.getCurrentAddress(), cidrAddress, bindAddress, existing);
            }
        }
    }

    @Override
    protected void rollbackRuntime(final OperationContext context, final ModelNode operation, final Resource resource) {
        getWorkerService(context).getBindingsTable().removeExact(
            Inet.parseCidrAddress(operation.require("match").asString()),
            new InetSocketAddress(
                Inet.parseInetAddress(operation.require("bind-address").asString()),
                operation.get("bind-port").asInt(0)
            )
        );
    }
}
