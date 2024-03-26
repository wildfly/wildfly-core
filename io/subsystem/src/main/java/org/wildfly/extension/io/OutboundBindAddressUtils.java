/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
import org.wildfly.io.IOServiceDescriptor;

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
        final ServiceName workerServiceName = context.getCapabilityServiceName(IOServiceDescriptor.WORKER, workerName);
        final ServiceController<?> workerServiceController = serviceRegistry.getRequiredService(workerServiceName);
        return (WorkerService) workerServiceController.getService();
    }
}
