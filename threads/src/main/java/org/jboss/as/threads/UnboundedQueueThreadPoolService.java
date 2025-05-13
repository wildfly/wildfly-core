/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.threads.JBossThreadPoolExecutor;

/**
 * Service responsible for creating, starting and stopping a thread pool executor with an unbounded queue.
 *
 * @author John E. Bailey
 */
public class UnboundedQueueThreadPoolService implements Service<ManagedJBossThreadPoolExecutorService> {
    private final InjectedValue<ThreadFactory> threadFactoryValue = new InjectedValue<>();

    private ManagedJBossThreadPoolExecutorService executor;

    private int maxThreads;
    private TimeSpec keepAlive;
    private boolean allowCoreThreadTimeout;

    public UnboundedQueueThreadPoolService(boolean allowCoreThreadTimeout, int maxThreads, TimeSpec keepAlive) {
        this.maxThreads = maxThreads;
        this.keepAlive = keepAlive;
        this.allowCoreThreadTimeout= allowCoreThreadTimeout;
    }

    public synchronized void start(final StartContext context) throws StartException {
        final TimeSpec keepAliveSpec = keepAlive;
        long keepAliveTime = keepAliveSpec == null ? Long.MAX_VALUE : keepAliveSpec.getUnit().toNanos(keepAliveSpec.getDuration());
        final JBossThreadPoolExecutor jbossExecutor = new JBossThreadPoolExecutor(maxThreads, maxThreads, keepAliveTime, TimeUnit.NANOSECONDS, new LinkedBlockingQueue<>(), threadFactoryValue.getValue());
        jbossExecutor.setAllowCoreThreadTimeout(allowCoreThreadTimeout);
        executor = new ManagedJBossThreadPoolExecutorService(jbossExecutor);
    }

    public void stop(final StopContext context) {
        final ManagedJBossThreadPoolExecutorService executor;
        synchronized (this) {
            executor = this.executor;
            this.executor = null;
        }
        context.asynchronous();
        executor.internalShutdown();
        executor.addShutdownListener(StopContextEventListener.getInstance(), context);
    }

    public synchronized ManagedJBossThreadPoolExecutorService getValue() throws IllegalStateException {
        final ManagedJBossThreadPoolExecutorService value = this.executor;
        if (value == null) {
            throw ThreadsLogger.ROOT_LOGGER.unboundedQueueThreadPoolExecutorUninitialized();
        }
        return value;
    }

    public Injector<ThreadFactory> getThreadFactoryInjector() {
        return threadFactoryValue;
    }

    public synchronized void setMaxThreads(final int maxThreads) {
        final ManagedJBossThreadPoolExecutorService executor = this.executor;
        if(executor != null) {
            if (maxThreads < this.maxThreads) {
                executor.setCoreThreads(maxThreads);
                executor.setMaxThreads(maxThreads);
            } else {
                executor.setMaxThreads(maxThreads);
                executor.setCoreThreads(maxThreads);
            }
        }
        this.maxThreads = maxThreads;
    }

    public synchronized void setKeepAlive(final TimeSpec keepAlive) {
        this.keepAlive = keepAlive;
        final ManagedJBossThreadPoolExecutorService executor = this.executor;
        if(executor != null) {
            executor.setKeepAlive(keepAlive);
        }
    }

    public int getActiveCount() {
        final ManagedJBossThreadPoolExecutorService executor = getValue();
        return executor.getActiveCount();
    }

    public long getCompletedTaskCount() {
        final ManagedJBossThreadPoolExecutorService executor = getValue();
        return executor.getCompletedTaskCount();
    }

    public int getCurrentThreadCount() {
        final ManagedJBossThreadPoolExecutorService executor = getValue();
        return executor.getCurrentThreadCount();
    }

    public int getLargestPoolSize() {
        final ManagedJBossThreadPoolExecutorService executor = getValue();
        return executor.getLargestPoolSize();
    }

    public int getLargestThreadCount() {
        final ManagedJBossThreadPoolExecutorService executor = getValue();
        return executor.getLargestThreadCount();
    }

    public int getRejectedCount() {
        final ManagedJBossThreadPoolExecutorService executor = getValue();
        return executor.getRejectedCount();
    }

    public long getTaskCount() {
        final ManagedJBossThreadPoolExecutorService executor = getValue();
        return executor.getTaskCount();
    }

    public int getQueueSize() {
        final ManagedJBossThreadPoolExecutorService executor = getValue();
        return executor.getQueueSize();
    }

    TimeUnit getKeepAliveUnit() {
        return keepAlive == null ? TimeSpec.DEFAULT_KEEPALIVE.getUnit() : keepAlive.getUnit();
    }
}
