/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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

    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
        final CidrAddressTable<InetSocketAddress> bindingsTable = getWorkerService(context).getBindingsTable();
        if (bindingsTable != null) {
            final CidrAddress cidrAddress = getCidrAddress(model, context);
            final InetSocketAddress bindAddress = getBindAddress(model, context);
            bindingsTable.removeExact(cidrAddress, bindAddress);
        }
    }
}
