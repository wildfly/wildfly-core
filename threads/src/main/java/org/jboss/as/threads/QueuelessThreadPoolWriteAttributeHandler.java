/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;



import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;


/**
 * Handles attribute writes for a queueless thread pool.
 *
 * @author Alexey Loubyansky
 */
public class QueuelessThreadPoolWriteAttributeHandler extends ThreadsWriteAttributeOperationHandler {

    private final ServiceName serviceNameBase;
    private final RuntimeCapability capability;

    public QueuelessThreadPoolWriteAttributeHandler(boolean blocking, final RuntimeCapability capability, ServiceName serviceNameBase) {
        super(blocking ? QueuelessThreadPoolAdd.BLOCKING_ATTRIBUTES : QueuelessThreadPoolAdd.NON_BLOCKING_ATTRIBUTES, QueuelessThreadPoolAdd.RW_ATTRIBUTES);
        this.serviceNameBase = serviceNameBase;
        this.capability = capability;
    }

    @Override
    protected void applyOperation(final OperationContext context, ModelNode model, String attributeName,
                                  ServiceController<?> service, boolean forRollback) throws OperationFailedException {

        final QueuelessThreadPoolService pool =  (QueuelessThreadPoolService) service.getService();

        if (PoolAttributeDefinitions.KEEPALIVE_TIME.getName().equals(attributeName)) {
            TimeUnit defaultUnit = pool.getKeepAliveUnit();
            final TimeSpec spec = getTimeSpec(context, model, defaultUnit);
            pool.setKeepAlive(spec);
        } else if(PoolAttributeDefinitions.MAX_THREADS.getName().equals(attributeName)) {
            pool.setMaxThreads(PoolAttributeDefinitions.MAX_THREADS.resolveModelAttribute(context, model).asInt());
        } else if (!forRollback) {
            // Programming bug. Throw a RuntimeException, not OFE, as this is not a client error
            throw ThreadsLogger.ROOT_LOGGER.unsupportedQueuelessThreadPoolAttribute(attributeName);
        }
    }

    @Override
    protected ServiceController<?> getService(final OperationContext context, final ModelNode model) throws OperationFailedException {
        final String name = context.getCurrentAddressValue();
        ServiceName serviceName = null;
        ServiceController<?> controller = null;
        if(capability != null) {
            serviceName = capability.getCapabilityServiceName(context.getCurrentAddress());
            controller = context.getServiceRegistry(true).getService(serviceName);
            if(controller != null) {
                return controller;
            }
        }
        if (serviceNameBase != null) {
            serviceName = serviceNameBase.append(name);
            controller = context.getServiceRegistry(true).getService(serviceName);
        }
        if(controller == null) {
            throw ThreadsLogger.ROOT_LOGGER.queuelessThreadPoolServiceNotFound(serviceName);
        }
        return controller;
    }
}
