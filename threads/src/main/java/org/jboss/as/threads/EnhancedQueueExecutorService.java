/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.threads;

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
class EnhancedQueueExecutorService implements Service<ManagedEnhancedQueueExecutor> {
    private final InjectedValue<ThreadFactory> threadFactoryValue = new InjectedValue<ThreadFactory>();

    private ManagedEnhancedQueueExecutor executor;

    private int maxThreads;
    private int coreThreads;
    private TimeSpec keepAlive;
    private boolean allowCoreThreadTimeout;

    EnhancedQueueExecutorService(boolean allowCoreThreadTimeout, int maxThreads, int coreThreads, TimeSpec keepAlive) {
        this.maxThreads = maxThreads;
        this.coreThreads = coreThreads;
        this.keepAlive = keepAlive;
        this.allowCoreThreadTimeout = allowCoreThreadTimeout;
    }

    public synchronized void start(final StartContext context) {
        final TimeSpec keepAliveSpec = keepAlive;
        long keepAliveTime = keepAliveSpec == null ? Long.MAX_VALUE : keepAliveSpec.getUnit().toNanos(keepAliveSpec.getDuration());

        final EnhancedQueueExecutor enhancedQueueExecutor = new EnhancedQueueExecutor.Builder()
                .setMaximumPoolSize(maxThreads)
                .setCorePoolSize(coreThreads > 0 ? coreThreads : maxThreads)
                .setKeepAliveTime(keepAliveTime, TimeUnit.NANOSECONDS)
                .setThreadFactory(threadFactoryValue.getValue())
                .allowCoreThreadTimeOut(allowCoreThreadTimeout)
                .build();
        executor = new ManagedEnhancedQueueExecutor(enhancedQueueExecutor);
    }

    public void stop(final StopContext context) {
        final ManagedEnhancedQueueExecutor executor;
        synchronized (this) {
            executor = this.executor;
            this.executor = null;
        }
        context.asynchronous();
        executor.internalShutdown();
        executor.addShutdownListener(StopContextEventListener.getInstance(), context);
    }

    public synchronized ManagedEnhancedQueueExecutor getValue() throws IllegalStateException {
        final ManagedEnhancedQueueExecutor value = this.executor;
        if (value == null) {
            throw ThreadsLogger.ROOT_LOGGER.enhancedQueueExecutorUninitialized();
        }
        return value;
    }

    Injector<ThreadFactory> getThreadFactoryInjector() {
        return threadFactoryValue;
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

    int getActiveCount() {
        final ManagedEnhancedQueueExecutor executor = getValue();
        return executor.getActiveCount();
    }

    long getCompletedTaskCount() {
        final ManagedEnhancedQueueExecutor executor = getValue();
        return executor.getCompletedTaskCount();
    }

    int getCurrentThreadCount() {
        final ManagedEnhancedQueueExecutor executor = getValue();
        return executor.getCurrentThreadCount();
    }

    int getLargestPoolSize() {
        final ManagedEnhancedQueueExecutor executor = getValue();
        return executor.getLargestPoolSize();
    }

    int getLargestThreadCount() {
        final ManagedEnhancedQueueExecutor executor = getValue();
        return executor.getLargestThreadCount();
    }

    int getRejectedCount() {
        final ManagedEnhancedQueueExecutor executor = getValue();
        return executor.getRejectedCount();
    }

    long getTaskCount() {
        final ManagedEnhancedQueueExecutor executor = getValue();
        return executor.getTaskCount();
    }

    int getQueueSize() {
        final ManagedEnhancedQueueExecutor executor = getValue();
        return executor.getQueueSize();
    }

    TimeUnit getKeepAliveUnit() {
        return keepAlive == null ? TimeSpec.DEFAULT_KEEPALIVE.getUnit() : keepAlive.getUnit();
    }
}
