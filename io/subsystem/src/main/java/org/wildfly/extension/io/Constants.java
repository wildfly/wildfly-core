/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 */
interface Constants {
    String BUFFER_POOL = "buffer-pool";
    String BUFFER_SIZE = "buffer-size";
    String BUFFER_PER_SLICE = "buffers-per-slice";
    String DIRECT_BUFFERS = "direct-buffers";
    String WORKER = "worker";
    String WORKER_IO_THREADS = "io-threads";
    String WORKER_TASK_CORE_THREADS = "task-core-threads";
    String WORKER_TASK_KEEPALIVE = "task-keepalive";
    String WORKER_TASK_LIMIT = "task-limit";
    String WORKER_TASK_MAX_THREADS = "task-max-threads";
    String THREAD_DAEMON = "thread-daemon";
    String STACK_SIZE = "stack-size";
}
