/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import static org.wildfly.extension.io.OutboundBindAddressUtils.getBindAddress;
import static org.wildfly.extension.io.OutboundBindAddressUtils.getCidrAddress;
import static org.wildfly.extension.io.OutboundBindAddressUtils.getWorkerService;

import java.net.InetSocketAddress;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.wildfly.common.net.CidrAddress;
import org.wildfly.common.net.CidrAddressTable;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class OutboundBindAddressRemoveHandler extends AbstractRemoveStepHandler {

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
        final CidrAddressTable<InetSocketAddress> bindingsTable = getWorkerService(context).getBindingsTable();
        if (bindingsTable != null) {
            final CidrAddress cidrAddress = getCidrAddress(model, context);
            final InetSocketAddress bindAddress = getBindAddress(model, context);
            bindingsTable.removeExact(cidrAddress, bindAddress);
        }
    }
}
