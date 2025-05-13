/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;


import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;


/**
 * Handles metrics for a bounded queue thread pool.
 *
 * @author Alexey Loubyansky
 */
public class BoundedQueueThreadPoolMetricsHandler extends ThreadPoolMetricsHandler {

    public static final List<AttributeDefinition> METRICS = Arrays.asList(PoolAttributeDefinitions.CURRENT_THREAD_COUNT,
            PoolAttributeDefinitions.LARGEST_THREAD_COUNT, PoolAttributeDefinitions.REJECTED_COUNT,
            PoolAttributeDefinitions.QUEUE_SIZE);

    public BoundedQueueThreadPoolMetricsHandler(final RuntimeCapability<Void> capability, final ServiceName serviceNameBase) {
        super(METRICS, capability, serviceNameBase);
    }

    @Override
    protected void setResult(OperationContext context, final String attributeName, final Service<?> service) {
        EnhancedQueueExecutorService bounded = (EnhancedQueueExecutorService) service;
        switch (attributeName) {
            case CommonAttributes.CURRENT_THREAD_COUNT:
                context.getResult().set(bounded.getCurrentThreadCount());
                break;
            case CommonAttributes.LARGEST_THREAD_COUNT:
                context.getResult().set(bounded.getLargestThreadCount());
                break;
            case CommonAttributes.REJECTED_COUNT:
                context.getResult().set(bounded.getRejectedCount());
                break;
            case CommonAttributes.QUEUE_SIZE:
                context.getResult().set(bounded.getQueueSize());
                break;
            default:
                // Programming bug. Throw a RuntimeException, not OFE, as this is not a client error
                throw ThreadsLogger.ROOT_LOGGER.unsupportedBoundedQueueThreadPoolMetric(attributeName);
        }
    }
}
