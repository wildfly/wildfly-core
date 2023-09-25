/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.threads.QueueExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for creating, starting and stopping a thread pool executor with a bounded queue.
 *
 * @author John E. Bailey
 */
public class BoundedQueueThreadPoolService implements Service<ManagedQueueExecutorService> {

    private final InjectedValue<ThreadFactory> threadFactoryValue = new InjectedValue<ThreadFactory>();
    private final InjectedValue<Executor> handoffExecutorValue = new InjectedValue<Executor>();
    private final boolean blocking;
    private final int queueLength;

    private ManagedQueueExecutorService executor;

    private int coreThreads;
    private int maxThreads;
    private TimeSpec keepAlive;
    private boolean allowCoreTimeout;

    public BoundedQueueThreadPoolService(int coreThreads, int maxThreads, int queueLength, boolean blocking, TimeSpec keepAlive, boolean allowCoreTimeout) {
        this.coreThreads = coreThreads;
        this.maxThreads = maxThreads;
        this.queueLength = queueLength;
        this.blocking = blocking;
        this.keepAlive = keepAlive;
        this.allowCoreTimeout = allowCoreTimeout;
    }

    public synchronized void start(final StartContext context) throws StartException {
        final TimeSpec keepAliveSpec = keepAlive;
        long keepAliveTime = keepAliveSpec == null ? Long.MAX_VALUE : keepAliveSpec.getUnit().toNanos(keepAliveSpec.getDuration());
        QueueExecutor queueExecutor = new QueueExecutor(coreThreads, maxThreads, keepAliveTime, TimeUnit.NANOSECONDS, queueLength, threadFactoryValue.getValue(), blocking, handoffExecutorValue.getOptionalValue());
        queueExecutor.setAllowCoreThreadTimeout(allowCoreTimeout);
        executor = new ManagedQueueExecutorService(queueExecutor);
    }

    public void stop(final StopContext context) {
        final ManagedQueueExecutorService executor;
        synchronized (this) {
            executor = this.executor;
            this.executor = null;
        }
        context.asynchronous();
        executor.internalShutdown();
        executor.addShutdownListener(StopContextEventListener.getInstance(), context);
    }

    public ManagedQueueExecutorService getValue() throws IllegalStateException {
        final ManagedQueueExecutorService value;
        synchronized (this) {
            value = this.executor;
        }
        if (value == null) {
            throw ThreadsLogger.ROOT_LOGGER.boundedQueueThreadPoolExecutorUninitialized();
        }
        return value;
    }

    public Injector<ThreadFactory> getThreadFactoryInjector() {
        return threadFactoryValue;
    }

    public Injector<Executor> getHandoffExecutorInjector() {
        return handoffExecutorValue;
    }

    public synchronized void setCoreThreads(int coreThreads) {
        this.coreThreads = coreThreads;
        final ManagedQueueExecutorService executor = this.executor;
        if(executor != null) {
            executor.setCoreThreads(coreThreads);
        }
    }

    public synchronized void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
        final ManagedQueueExecutorService executor = this.executor;
        if(executor != null) {
            executor.setMaxThreads(maxThreads);
        }
    }

    public synchronized void setKeepAlive(TimeSpec keepAlive) {
        this.keepAlive = keepAlive;
        final ManagedQueueExecutorService executor = this.executor;
        if(executor != null) {
            executor.setKeepAlive(keepAlive);
        }
    }

    public synchronized void setAllowCoreTimeout(boolean allowCoreTimeout) {
        this.allowCoreTimeout = allowCoreTimeout;
        final ManagedQueueExecutorService executor = this.executor;
        if(executor != null) {
            executor.setAllowCoreTimeout(allowCoreTimeout);
        }
    }

    public int getCurrentThreadCount() {
        final ManagedQueueExecutorService executor = getValue();
        return executor.getCurrentThreadCount();
    }

    public int getLargestThreadCount() {
        final ManagedQueueExecutorService executor = getValue();
        return executor.getLargestThreadCount();
    }

    TimeUnit getKeepAliveUnit() {
        return keepAlive == null ? TimeSpec.DEFAULT_KEEPALIVE.getUnit() : keepAlive.getUnit();
    }

    public int getRejectedCount() {
        final ManagedQueueExecutorService executor = getValue();
        return executor.getRejectedCount();
    }
    public int getQueueSize() {
        final ManagedQueueExecutorService executor = getValue();
        return executor.getQueueSize();
    }
}
