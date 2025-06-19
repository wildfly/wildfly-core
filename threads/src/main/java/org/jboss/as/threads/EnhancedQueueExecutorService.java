/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.threads.EnhancedQueueExecutor;

/**
 * Service responsible for creating, starting and stopping an {@code org.jboss.threads.EnhancedQueueExecutor}.
 */
class EnhancedQueueExecutorService implements Service<ManagedQueueExecutorService> {
    private final InjectedValue<ThreadFactory> threadFactoryValue = new InjectedValue<>();
    private final InjectedValue<Executor> handoffExecutorValue = new InjectedValue<>();

    private ManagedEnhancedQueueExecutor executor;

    private int maxThreads;
    private int coreThreads;
    private final int queueLength;
    private TimeSpec keepAlive;
    private boolean allowCoreThreadTimeout;
    private final boolean blocking;

    EnhancedQueueExecutorService(int maxThreads, TimeSpec keepAlive, boolean blocking) {
        this(false, maxThreads, 0, Integer.MAX_VALUE, keepAlive, blocking);
    }

    EnhancedQueueExecutorService(boolean allowCoreThreadTimeout, int maxThreads, int coreThreads,
                                 int queueLength, TimeSpec keepAlive, boolean blocking) {
        this.maxThreads = maxThreads;
        this.coreThreads = coreThreads;
        this.queueLength = queueLength;
        this.keepAlive = keepAlive;
        this.allowCoreThreadTimeout = allowCoreThreadTimeout;
        this.blocking = blocking;
    }

    public synchronized void start(final StartContext context) {
        final TimeSpec keepAliveSpec = keepAlive;
        long keepAliveTime = keepAliveSpec == null ? Long.MAX_VALUE : keepAliveSpec.getUnit().toNanos(keepAliveSpec.getDuration());

        EnhancedQueueExecutor.Builder executorBuilder = new EnhancedQueueExecutor.Builder()
                .setMaximumPoolSize(maxThreads)
                .setCorePoolSize(coreThreads > 0 ? coreThreads : maxThreads)
                .setMaximumQueueSize(queueLength)
                .setKeepAliveTime(keepAliveTime, TimeUnit.NANOSECONDS)
                .setThreadFactory(threadFactoryValue.getValue())
                .allowCoreThreadTimeOut(allowCoreThreadTimeout);
        Executor handoffExecutor = handoffExecutorValue.getOptionalValue();
        if (handoffExecutor != null) {
            executorBuilder.setHandoffExecutor(handoffExecutor);
        }
        EnhancedQueueExecutor enhancedQueueExecutor = executorBuilder.build();
        this.executor = new ManagedEnhancedQueueExecutor(enhancedQueueExecutor, blocking);
    }

    public void stop(final StopContext context) {
        final ManagedEnhancedQueueExecutor executor;
        synchronized (this) {
            executor = this.executor;
            this.executor = null;
        }
        context.asynchronous();
        executor.internalShutdown(context);
    }

    public synchronized ManagedQueueExecutorService getValue() throws IllegalStateException {
        final ManagedQueueExecutorService value = this.executor;
        if (value == null) {
            throw ThreadsLogger.ROOT_LOGGER.enhancedQueueExecutorUninitialized();
        }
        return value;
    }

    Injector<ThreadFactory> getThreadFactoryInjector() {
        return threadFactoryValue;
    }

    Injector<Executor> getHandoffExecutorInjector() {
        return handoffExecutorValue;
    }

    synchronized void setMaxThreads(final int maxThreads) {
        final ManagedEnhancedQueueExecutor executor = this.executor;
        if (executor != null) {
            executor.setMaxThreads(maxThreads);
        }
        this.maxThreads = maxThreads;
    }

    synchronized void setCoreThreads(final int coreThreads) {
        final ManagedEnhancedQueueExecutor executor = this.executor;
        if (executor != null) {
            executor.setCoreThreads(coreThreads);
        }
        this.coreThreads = coreThreads;
    }

    synchronized void setKeepAlive(final TimeSpec keepAlive) {
        this.keepAlive = keepAlive;
        final ManagedEnhancedQueueExecutor executor = this.executor;
        if (executor != null) {
            executor.setKeepAlive(keepAlive);
        }
    }

    synchronized void setAllowCoreTimeout(final boolean allowCoreThreadTimeout) {
        final ManagedEnhancedQueueExecutor executor = this.executor;
        if (executor != null) {
            executor.setAllowCoreTimeout(allowCoreThreadTimeout);
        }
        this.allowCoreThreadTimeout = allowCoreThreadTimeout;
    }

    int getActiveCount() {
        final ManagedQueueExecutorService executor = getValue();
        return executor.getActiveCount();
    }

    long getCompletedTaskCount() {
        final ManagedQueueExecutorService executor = getValue();
        return executor.getCompletedTaskCount();
    }

    int getCurrentThreadCount() {
        final ManagedQueueExecutorService executor = getValue();
        return executor.getCurrentThreadCount();
    }

    int getLargestPoolSize() {
        final ManagedQueueExecutorService executor = getValue();
        return executor.getLargestPoolSize();
    }

    int getLargestThreadCount() {
        final ManagedQueueExecutorService executor = getValue();
        return executor.getLargestThreadCount();
    }

    int getRejectedCount() {
        final ManagedQueueExecutorService executor = getValue();
        return executor.getRejectedCount();
    }

    long getTaskCount() {
        final ManagedQueueExecutorService executor = getValue();
        return executor.getTaskCount();
    }

    int getQueueSize() {
        final ManagedQueueExecutorService executor = getValue();
        return executor.getQueueSize();
    }

    TimeUnit getKeepAliveUnit() {
        return keepAlive == null ? TimeSpec.DEFAULT_KEEPALIVE.getUnit() : keepAlive.getUnit();
    }
}
