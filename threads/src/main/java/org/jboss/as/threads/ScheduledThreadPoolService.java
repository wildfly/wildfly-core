/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

/**
 * Service responsible for creating, starting and stopping a scheduled thread pool executor.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ScheduledThreadPoolService implements Service<ManagedScheduledExecutorService> {

    private final InjectedValue<ThreadFactory> threadFactoryValue = new InjectedValue<ThreadFactory>();

    private ManagedScheduledExecutorService executor;
    private StopContext context;

    private final int maxThreads;
    private final TimeSpec keepAlive;

    public ScheduledThreadPoolService(final int maxThreads, final TimeSpec keepAlive) {
        this.maxThreads = maxThreads;
        this.keepAlive = keepAlive;
    }

    public void start(final StartContext context) throws StartException {
        ScheduledThreadPoolExecutor scheduledExecutor = new ExecutorImpl(0, threadFactoryValue.getValue());
        scheduledExecutor.setCorePoolSize(maxThreads);
        if (keepAlive != null) scheduledExecutor.setKeepAliveTime(keepAlive.getDuration(), keepAlive.getUnit());
        final ManagedScheduledExecutorService executorService = new ManagedScheduledExecutorService(scheduledExecutor);
        synchronized (this) {
            executor = executorService;
        }
    }

    public void stop(final StopContext context) {
        final ManagedScheduledExecutorService executor;
        synchronized (this) {
            executor = this.executor;
            this.context = context;
            this.executor = null;
        }
        context.asynchronous();
        executor.internalShutdown();
    }

    public ManagedScheduledExecutorService getValue() throws IllegalStateException {
        final ManagedScheduledExecutorService value;
        synchronized (this) {
            value = this.executor;
        }
        if (value == null) {
            throw ThreadsLogger.ROOT_LOGGER.scheduledThreadPoolExecutorUninitialized();
        }
        return value;
    }

    public Injector<ThreadFactory> getThreadFactoryInjector() {
        return threadFactoryValue;
    }

    public int getActiveCount() {
        final ManagedScheduledExecutorService executor = getValue();
        return executor.getActiveCount();
    }

    public long getCompletedTaskCount() {
        final ManagedScheduledExecutorService executor = getValue();
        return executor.getCompletedTaskCount();
    }

    public int getCurrentThreadCount() {
        final ManagedScheduledExecutorService executor = getValue();
        return executor.getPoolSize();
    }

    public int getLargestThreadCount() {
        final ManagedScheduledExecutorService executor = getValue();
        return executor.getLargestPoolSize();
    }

    public long getTaskCount() {
        final ManagedScheduledExecutorService executor = getValue();
        return executor.getTaskCount();
    }

    public int getQueueSize() {
        final ManagedScheduledExecutorService executor = getValue();
        return executor.getQueueSize();
    }

    private class ExecutorImpl extends ScheduledThreadPoolExecutor {

        ExecutorImpl(final int corePoolSize, final ThreadFactory threadFactory) {
            super(corePoolSize, threadFactory);
        }

        protected void terminated() {
            super.terminated();
            StopContext context;
            synchronized (ScheduledThreadPoolService.this) {
                context = ScheduledThreadPoolService.this.context;
                ScheduledThreadPoolService.this.context = null;
            }
            context.complete();
        }
    }
}
