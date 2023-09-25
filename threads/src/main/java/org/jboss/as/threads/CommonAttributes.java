/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface CommonAttributes {
    String ACTIVE_COUNT = "active-count";
    String ALLOW_CORE_TIMEOUT = "allow-core-timeout";
    String BLOCKING = "blocking";
    String BLOCKING_BOUNDED_QUEUE_THREAD_POOL = "blocking-bounded-queue-thread-pool";
    String BLOCKING_QUEUELESS_THREAD_POOL = "blocking-queueless-thread-pool";
    String BOUNDED_QUEUE_THREAD_POOL = "bounded-queue-thread-pool";
    String COMPLETED_TASK_COUNT = "completed-task-count";
    String CORE_THREADS = "core-threads";
    String COUNT = "count";
    String CURRENT_THREAD_COUNT = "current-thread-count";
    String PER_CPU = "per-cpu";
    String HANDOFF_EXECUTOR = "handoff-executor";
    String LARGEST_THREAD_COUNT = "largest-thread-count";
    String NAME = "name";
    String GROUP_NAME = "group-name";
    String KEEPALIVE_TIME = "keepalive-time";
    String MAX_THREADS = "max-threads";
    String PRIORITY = "priority";
    String PROPERTIES = "properties";
    String PROPERTY = "property";
    String QUEUELESS_THREAD_POOL = "queueless-thread-pool";
    String QUEUE_LENGTH = "queue-length";
    String QUEUE_SIZE = "queue-size";
    String REJECTED_COUNT = "rejected-count";
    String SCHEDULED_THREAD_POOL = "scheduled-thread-pool";
    String TASK_COUNT = "task-count";
    String THREADS = "threads";
    String TIME = "time";
    String THREAD_FACTORY = "thread-factory";
    String THREAD_NAME_PATTERN = "thread-name-pattern";
    String UNBOUNDED_QUEUE_THREAD_POOL = "unbounded-queue-thread-pool";
    String ENHANCED_QUEUE_THREAD_POOL = "enhanced-queue-thread-pool";
    String UNIT = "unit";
    String VALUE = "value";
}
