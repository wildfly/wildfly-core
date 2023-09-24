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
 * Handles attribute writes for an {@code org.jboss.threads.EnhancedQueueExecutor}.
 */
class EnhancedQueueExecutorWriteAttributeHandler extends ThreadsWriteAttributeOperationHandler {

    private final ServiceName serviceNameBase;
    private final RuntimeCapability capability;

    EnhancedQueueExecutorWriteAttributeHandler(final RuntimeCapability capability, ServiceName serviceNameBase) {
        super(EnhancedQueueExecutorAdd.ATTRIBUTES, EnhancedQueueExecutorAdd.RW_ATTRIBUTES);
        this.serviceNameBase = serviceNameBase;
        this.capability = capability;
    }

    @Override
    protected void applyOperation(final OperationContext context, ModelNode model, String attributeName,
                                  ServiceController<?> service, boolean forRollback) throws OperationFailedException {

        final EnhancedQueueExecutorService pool = (EnhancedQueueExecutorService) service.getService();

        if (PoolAttributeDefinitions.KEEPALIVE_TIME.getName().equals(attributeName)) {
            TimeUnit defaultUnit = pool.getKeepAliveUnit();
            final TimeSpec spec = getTimeSpec(context, model, defaultUnit);
            pool.setKeepAlive(spec);
        } else if (PoolAttributeDefinitions.MAX_THREADS.getName().equals(attributeName)) {
            pool.setMaxThreads(PoolAttributeDefinitions.MAX_THREADS.resolveModelAttribute(context, model).asInt());
        } else if (PoolAttributeDefinitions.CORE_THREADS.getName().equals(attributeName)) {
            pool.setCoreThreads(PoolAttributeDefinitions.CORE_THREADS.resolveModelAttribute(context, model).asInt());
        } else if (!forRollback) {
            // Programming bug. Throw a RuntimeException, not OFE, as this is not a client error
            throw ThreadsLogger.ROOT_LOGGER.unsupportedEnhancedQueueExecutorAttribute(attributeName);
        }
    }

    @Override
    protected ServiceController<?> getService(final OperationContext context, final ModelNode model) throws OperationFailedException {
        final String name = context.getCurrentAddressValue();
        ServiceName serviceName = null;
        ServiceController<?> controller = null;
        if (capability != null) {
            serviceName = capability.getCapabilityServiceName(context.getCurrentAddress());
            controller = context.getServiceRegistry(true).getService(serviceName);
            if (controller != null) {
                return controller;
            }
        }
        if (serviceNameBase != null) {
            serviceName = serviceNameBase.append(name);
            controller = context.getServiceRegistry(true).getService(serviceName);
        }
        if (controller == null) {
            throw ThreadsLogger.ROOT_LOGGER.enhancedQueueExecutorServiceNotFound(serviceName);
        }
        return controller;
    }
}
