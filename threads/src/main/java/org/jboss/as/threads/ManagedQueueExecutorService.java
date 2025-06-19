/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;

import java.util.concurrent.ExecutorService;

/**
 * {@link ExecutorService} that provides hooks for integration
 * with a WildFly management resource.
 *
 * @author Alexey Loubyansky
 */
public interface ManagedQueueExecutorService extends ManagedExecutorService {


    /**
     * Gets whether this executor is configured to block calls to
     * {@link #execute(Runnable)} until {@link #getMaxThreads() thread capacity}
     * or {@link #getQueueSize() queue capacity} is available to handle the
     * provided task.
     *
     * @return {@code true} if this executor support blocking semantics; {@code false} otherwise
     */
    boolean isBlocking();

    int getCoreThreads();

    boolean isAllowCoreTimeout();

    int getMaxThreads();

    long getKeepAlive();

    int getRejectedCount();

    long getTaskCount();

    int getLargestThreadCount();

    int getLargestPoolSize();

    int getCurrentThreadCount();

    long getCompletedTaskCount();

    int getActiveCount();

    int getQueueSize();
}
