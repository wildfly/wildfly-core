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
 * Handles metrics for a scheduled thread pool.
 * @author Alexey Loubyansky
 */
public class ScheduledThreadPoolMetricsHandler extends ThreadPoolMetricsHandler {

    public static final List<AttributeDefinition> METRICS = Arrays.asList(PoolAttributeDefinitions.ACTIVE_COUNT,
            PoolAttributeDefinitions.COMPLETED_TASK_COUNT, PoolAttributeDefinitions.CURRENT_THREAD_COUNT,
            PoolAttributeDefinitions.LARGEST_THREAD_COUNT, PoolAttributeDefinitions.TASK_COUNT,
            PoolAttributeDefinitions.QUEUE_SIZE);

    public ScheduledThreadPoolMetricsHandler(RuntimeCapability<Void> capability, final ServiceName serviceNameBase) {
        super(METRICS, capability, serviceNameBase);
    }

    @Override
    protected void setResult(OperationContext context, final String attributeName, final Service<?> service) {
        final ScheduledThreadPoolService pool = (ScheduledThreadPoolService) service;
        if(attributeName.equals(CommonAttributes.ACTIVE_COUNT)) {
            context.getResult().set(pool.getActiveCount());
        } else if(attributeName.equals(CommonAttributes.COMPLETED_TASK_COUNT)) {
            context.getResult().set(pool.getCompletedTaskCount());
        } else if (attributeName.equals(CommonAttributes.CURRENT_THREAD_COUNT)) {
            context.getResult().set(pool.getCurrentThreadCount());
        } else if (attributeName.equals(CommonAttributes.LARGEST_THREAD_COUNT)) {
            context.getResult().set(pool.getLargestThreadCount());
        } else if (attributeName.equals(CommonAttributes.TASK_COUNT)) {
            context.getResult().set(pool.getTaskCount());
        } else if (attributeName.equals(CommonAttributes.QUEUE_SIZE)) {
            context.getResult().set(pool.getQueueSize());
        } else {
            // Programming bug. Throw a RuntimeException, not OFE, as this is not a client error
            throw ThreadsLogger.ROOT_LOGGER.unsupportedScheduledThreadPoolMetric(attributeName);
        }
    }
}
