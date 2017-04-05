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

import java.net.InetSocketAddress;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.wildfly.common.net.CidrAddress;
import org.wildfly.common.net.Inet;
import org.xnio.XnioWorker;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class OutboundBindAddressUtils {
    private OutboundBindAddressUtils() {
    }

    static InetSocketAddress getBindAddress(final ModelNode operation, final OperationContext context) throws OperationFailedException {
        return new InetSocketAddress(Inet.parseInetAddress(
            OutboundBindAddressResourceDefinition.BIND_ADDRESS.resolveModelAttribute(context, operation).asString()),
            OutboundBindAddressResourceDefinition.BIND_PORT.resolveModelAttribute(context, operation).asInt(0)
        );
    }

    static CidrAddress getCidrAddress(final ModelNode operation, final OperationContext context) throws OperationFailedException {
        return Inet.parseCidrAddress(OutboundBindAddressResourceDefinition.MATCH.resolveModelAttribute(context, operation).asString());
    }

    static WorkerService getWorkerService(final OperationContext context) {
        final ServiceRegistry serviceRegistry = context.getServiceRegistry(false);
        final String workerName = context.getCurrentAddress().getParent().getLastElement().getValue();
        final ServiceName workerServiceName = WorkerResourceDefinition.IO_WORKER_RUNTIME_CAPABILITY.getCapabilityServiceName(workerName, XnioWorker.class);
        final ServiceController<?> workerServiceController = serviceRegistry.getRequiredService(workerServiceName);
        return (WorkerService) workerServiceController.getService();
    }
}
