/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;


import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Attribute definitions used by thread pool resources.
 *
 * @author <a href="alex@jboss.org">Alexey Loubyansky</a>
 */
public interface PoolAttributeDefinitions {

    SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(CommonAttributes.NAME, ModelType.STRING, true)
            .setResourceOnly()
            .build();

    SimpleAttributeDefinition THREAD_FACTORY = new SimpleAttributeDefinitionBuilder(CommonAttributes.THREAD_FACTORY, ModelType.STRING, true)
            .setCapabilityReference("org.wildfly.threads.thread-factory")
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES).build();

    SimpleAttributeDefinition MAX_THREADS = new SimpleAttributeDefinitionBuilder(CommonAttributes.MAX_THREADS, ModelType.INT, false)
            .setValidator(new IntRangeValidator(0, Integer.MAX_VALUE, false, true)).setAllowExpression(true).build();

    KeepAliveTimeAttributeDefinition KEEPALIVE_TIME = new KeepAliveTimeAttributeDefinition();

    SimpleAttributeDefinition CORE_THREADS = new SimpleAttributeDefinitionBuilder(CommonAttributes.CORE_THREADS, ModelType.INT, true)
            .setValidator(new IntRangeValidator(0, Integer.MAX_VALUE, true, true)).setAllowExpression(true).build();

    SimpleAttributeDefinition HANDOFF_EXECUTOR = new SimpleAttributeDefinitionBuilder(CommonAttributes.HANDOFF_EXECUTOR, ModelType.STRING, true)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES).build();

    SimpleAttributeDefinition QUEUE_LENGTH = new SimpleAttributeDefinitionBuilder(CommonAttributes.QUEUE_LENGTH, ModelType.INT, false)
            .setValidator(new IntRangeValidator(1, Integer.MAX_VALUE, false, true)).setAllowExpression(true).setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES).build();

    SimpleAttributeDefinition ALLOW_CORE_TIMEOUT = new SimpleAttributeDefinitionBuilder(CommonAttributes.ALLOW_CORE_TIMEOUT, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .build();

    SimpleAttributeDefinition GROUP_NAME = new SimpleAttributeDefinitionBuilder(CommonAttributes.GROUP_NAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .build();

    SimpleAttributeDefinition THREAD_NAME_PATTERN = new SimpleAttributeDefinitionBuilder(CommonAttributes.THREAD_NAME_PATTERN, ModelType.STRING, true)
            .setAllowExpression(true)
            .build();

    SimpleAttributeDefinition PRIORITY = new SimpleAttributeDefinitionBuilder(CommonAttributes.PRIORITY, ModelType.INT, true)
            .setValidator(new IntRangeValidator(Thread.MIN_PRIORITY, Thread.MAX_PRIORITY, true, true))
            .setAllowExpression(true)
            .build();

    // Metrics
    AttributeDefinition CURRENT_THREAD_COUNT = new SimpleAttributeDefinitionBuilder(CommonAttributes.CURRENT_THREAD_COUNT, ModelType.INT)
            .setUndefinedMetricValue(ModelNode.ZERO)
            .build();
    AttributeDefinition LARGEST_THREAD_COUNT = new SimpleAttributeDefinitionBuilder(CommonAttributes.LARGEST_THREAD_COUNT, ModelType.INT)
            .setUndefinedMetricValue(ModelNode.ZERO)
            .build();
    AttributeDefinition REJECTED_COUNT = new SimpleAttributeDefinitionBuilder(CommonAttributes.REJECTED_COUNT, ModelType.INT)
            .setUndefinedMetricValue(ModelNode.ZERO)
            .build();
    AttributeDefinition ACTIVE_COUNT = new SimpleAttributeDefinitionBuilder(CommonAttributes.ACTIVE_COUNT, ModelType.INT)
            .setUndefinedMetricValue(ModelNode.ZERO)
            .build();
    AttributeDefinition COMPLETED_TASK_COUNT = new SimpleAttributeDefinitionBuilder(CommonAttributes.COMPLETED_TASK_COUNT, ModelType.INT)
            .setUndefinedMetricValue(ModelNode.ZERO)
            .build();
    AttributeDefinition TASK_COUNT = new SimpleAttributeDefinitionBuilder(CommonAttributes.TASK_COUNT, ModelType.INT)
            .setUndefinedMetricValue(ModelNode.ZERO)
            .build();
    AttributeDefinition QUEUE_SIZE = new SimpleAttributeDefinitionBuilder(CommonAttributes.QUEUE_SIZE, ModelType.INT)
            .setUndefinedMetricValue(ModelNode.ZERO)
            .build();
}
