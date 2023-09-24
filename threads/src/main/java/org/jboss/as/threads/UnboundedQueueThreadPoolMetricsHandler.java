/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;


import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;


/**
 * Handles metrics for an unbounded queue thread pool.
 * @author Alexey Loubyansky
 */
public class UnboundedQueueThreadPoolMetricsHandler extends ThreadPoolMetricsHandler {

    public static final List<AttributeDefinition> METRICS = Arrays.asList(PoolAttributeDefinitions.ACTIVE_COUNT,
            PoolAttributeDefinitions.COMPLETED_TASK_COUNT, PoolAttributeDefinitions.CURRENT_THREAD_COUNT,
            PoolAttributeDefinitions.LARGEST_THREAD_COUNT, PoolAttributeDefinitions.REJECTED_COUNT,
            PoolAttributeDefinitions.TASK_COUNT, PoolAttributeDefinitions.QUEUE_SIZE);

    public UnboundedQueueThreadPoolMetricsHandler(final ServiceName serviceNameBase) {
        this(null, serviceNameBase);
    }

    public UnboundedQueueThreadPoolMetricsHandler(final RuntimeCapability capability, final ServiceName serviceNameBase) {
        super(METRICS, capability, serviceNameBase);
    }

    @Override
    protected void setResult(OperationContext context, final String attributeName, final Service<?> service)
            throws OperationFailedException {
        final UnboundedQueueThreadPoolService pool = (UnboundedQueueThreadPoolService) service;
        switch (attributeName) {
            case CommonAttributes.ACTIVE_COUNT:
                context.getResult().set(pool.getActiveCount());
                break;
            case CommonAttributes.COMPLETED_TASK_COUNT:
                context.getResult().set(pool.getCompletedTaskCount());
                break;
            case CommonAttributes.CURRENT_THREAD_COUNT:
                context.getResult().set(pool.getCurrentThreadCount());
                break;
            case CommonAttributes.LARGEST_THREAD_COUNT:
                context.getResult().set(pool.getLargestThreadCount());
                break;
            case CommonAttributes.REJECTED_COUNT:
                context.getResult().set(pool.getRejectedCount());
                break;
            case CommonAttributes.TASK_COUNT:
                context.getResult().set(pool.getTaskCount());
                break;
            case CommonAttributes.QUEUE_SIZE:
                context.getResult().set(pool.getQueueSize());
                break;
            default:
                // Programming bug. Throw a RuntimeException, not OFE, as this is not a client error
                throw ThreadsLogger.ROOT_LOGGER.unsupportedUnboundedQueueThreadPoolMetric(attributeName);
        }
    }
}
