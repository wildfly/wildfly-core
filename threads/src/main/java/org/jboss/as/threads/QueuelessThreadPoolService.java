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
import org.jboss.threads.JBossExecutors;
import org.jboss.threads.QueuelessExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for creating, starting and stopping a thread pool executor with no queue.
 *
 * @author John E. Bailey
 */
public class QueuelessThreadPoolService implements Service<ManagedQueuelessExecutorService> {
    private final InjectedValue<ThreadFactory> threadFactoryValue = new InjectedValue<ThreadFactory>();
    private final InjectedValue<Executor> handoffExecutorValue = new InjectedValue<Executor>();
    private final boolean blocking;

    private ManagedQueuelessExecutorService executor;

    private int maxThreads;
    private TimeSpec keepAlive;

    public QueuelessThreadPoolService(int maxThreads, boolean blocking, TimeSpec keepAlive) {
        this.maxThreads = maxThreads;
        this.blocking = blocking;
        this.keepAlive = keepAlive;
    }

    public synchronized void start(final StartContext context) throws StartException {
        final TimeSpec keepAliveSpec = keepAlive;
        long keepAlive = keepAliveSpec == null ? Long.MAX_VALUE : keepAliveSpec.getUnit().toMillis(keepAliveSpec.getDuration());
        final QueuelessExecutor queuelessExecutor = new QueuelessExecutor(threadFactoryValue.getValue(), JBossExecutors.directExecutor(), handoffExecutorValue.getOptionalValue(), keepAlive);
        queuelessExecutor.setMaxThreads(maxThreads);
        queuelessExecutor.setBlocking(blocking);
        executor = new ManagedQueuelessExecutorService(queuelessExecutor);
    }

    public void stop(final StopContext context) {
        final ManagedQueuelessExecutorService executor;
        synchronized (this) {
            executor = this.executor;
            this.executor = null;
        }
        context.asynchronous();
        executor.internalShutdown();
        executor.addShutdownListener(StopContextEventListener.getInstance(), context);
    }

    public ManagedQueuelessExecutorService getValue() throws IllegalStateException {
        final ManagedQueuelessExecutorService value;
        synchronized (this) {
            value = this.executor;
        }
        if (value == null) {
            throw ThreadsLogger.ROOT_LOGGER.queuelessThreadPoolExecutorUninitialized();
        }
        return value;
    }

    public Injector<ThreadFactory> getThreadFactoryInjector() {
        return threadFactoryValue;
    }

    public Injector<Executor> getHandoffExecutorInjector() {
        return handoffExecutorValue;
    }

    public synchronized void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
        final ManagedQueuelessExecutorService executor = this.executor;
        if(executor != null) {
            executor.setMaxThreads(maxThreads);
        }
    }

    public synchronized void setKeepAlive(TimeSpec keepAliveSpec) {
        keepAlive = keepAliveSpec;
        final ManagedQueuelessExecutorService executor = this.executor;
        if(executor != null) {
            long keepAlive = keepAliveSpec == null ? Long.MAX_VALUE : keepAliveSpec.getDuration();
            executor.setKeepAlive(keepAlive);
        }
    }

    public int getCurrentThreadCount() {
        final ManagedQueuelessExecutorService executor = getValue();
        return executor.getCurrentThreadCount();
    }

    public int getLargestThreadCount() {
        final ManagedQueuelessExecutorService executor = getValue();
        return executor.getLargestThreadCount();
    }

    public int getRejectedCount() {
        final ManagedQueuelessExecutorService executor = getValue();
        return executor.getRejectedCount();
    }

    public int getQueueSize() {
        return executor.getQueueSize();
    }

    TimeUnit getKeepAliveUnit() {
        return keepAlive == null ? TimeSpec.DEFAULT_KEEPALIVE.getUnit() : keepAlive.getUnit();
    }
}
