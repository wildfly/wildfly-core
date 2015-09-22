/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jboss.threads.JBossThreadPoolExecutorReuseIdleThreads;

/**
 * Service responsible for creating, starting and stopping a thread pool executor with an unbounded queue.
 *
 * @author John E. Bailey
 */
public class UnboundedQueueThreadPoolService implements Service<ManagedJBossThreadPoolExecutorReuseIdleThreadsService> {
    private final InjectedValue<ThreadFactory> threadFactoryValue = new InjectedValue<ThreadFactory>();

    private ManagedJBossThreadPoolExecutorReuseIdleThreadsService executor;

    private int maxThreads;
    private int coreThreads;
    private TimeSpec keepAlive;

    public UnboundedQueueThreadPoolService(int maxThreads, TimeSpec keepAlive) {
        this.maxThreads = maxThreads;
        this.keepAlive = keepAlive;
        this.coreThreads = maxThreads;
    }

    public UnboundedQueueThreadPoolService(int coreThreads, int maxThreads, TimeSpec keepAlive) {
        this.maxThreads = maxThreads;
        this.coreThreads = coreThreads;
        this.keepAlive = keepAlive;
    }

    public synchronized void start(final StartContext context) throws StartException {
        final TimeSpec keepAliveSpec = keepAlive;
        long keepAliveTime = keepAliveSpec == null ? Long.MAX_VALUE : keepAliveSpec.getUnit().toNanos(keepAliveSpec.getDuration());
        final JBossThreadPoolExecutorReuseIdleThreads jbossExecutor = new JBossThreadPoolExecutorReuseIdleThreads(coreThreads, maxThreads, keepAliveTime, TimeUnit.NANOSECONDS,
              new LinkedBlockingQueue<Runnable>(), threadFactoryValue.getValue());
        executor = new ManagedJBossThreadPoolExecutorReuseIdleThreadsService(jbossExecutor);
    }

    public void stop(final StopContext context) {
        final ManagedJBossThreadPoolExecutorReuseIdleThreadsService executor;
        synchronized (this) {
            executor = this.executor;
            this.executor = null;
        }
        context.asynchronous();
        executor.internalShutdown();
        executor.addShutdownListener(StopContextEventListener.getInstance(), context);
    }

    public synchronized ManagedJBossThreadPoolExecutorReuseIdleThreadsService getValue() throws IllegalStateException {
        final ManagedJBossThreadPoolExecutorReuseIdleThreadsService value = this.executor;
        if (value == null) {
            throw ThreadsLogger.ROOT_LOGGER.unboundedQueueThreadPoolExecutorUninitialized();
        }
        return value;
    }

    public Injector<ThreadFactory> getThreadFactoryInjector() {
        return threadFactoryValue;
    }

    public synchronized void setMaxThreads(final int maxThreads) {
        final ManagedJBossThreadPoolExecutorReuseIdleThreadsService executor = this.executor;
        if(executor != null) {
            if (maxThreads < this.maxThreads) {
                executor.setCoreThreads(coreThreads);
                executor.setMaxThreads(maxThreads);
            } else {
                executor.setMaxThreads(maxThreads);
                executor.setCoreThreads(coreThreads);
            }
        }
        this.maxThreads = maxThreads;
    }

    public synchronized void setCoreThreads(final int coreThreads) {
        final ManagedJBossThreadPoolExecutorReuseIdleThreadsService executor = this.executor;
        if(executor != null) {
            if (coreThreads != this.coreThreads) {
                executor.setCoreThreads(coreThreads);
            }
        }
        this.coreThreads = coreThreads;
    }

    public synchronized void setKeepAlive(final TimeSpec keepAlive) {
        this.keepAlive = keepAlive;
        final ManagedJBossThreadPoolExecutorReuseIdleThreadsService executor = this.executor;
        if(executor != null) {
            executor.setKeepAlive(keepAlive);
        }
    }

    public int getActiveCount() {
        final ManagedJBossThreadPoolExecutorReuseIdleThreadsService executor = getValue();
        return executor.getActiveCount();
    }

    public long getCompletedTaskCount() {
        final ManagedJBossThreadPoolExecutorReuseIdleThreadsService executor = getValue();
        return executor.getCompletedTaskCount();
    }

    public int getCurrentThreadCount() {
        final ManagedJBossThreadPoolExecutorReuseIdleThreadsService executor = getValue();
        return executor.getCurrentThreadCount();
    }

    public int getLargestPoolSize() {
        final ManagedJBossThreadPoolExecutorReuseIdleThreadsService executor = getValue();
        return executor.getLargestPoolSize();
    }

    public int getLargestThreadCount() {
        final ManagedJBossThreadPoolExecutorReuseIdleThreadsService executor = getValue();
        return executor.getLargestThreadCount();
    }

    public int getCoreThreads() {
        final ManagedJBossThreadPoolExecutorReuseIdleThreadsService executor = getValue();
        return executor.getCoreThreads();
    }

    public int getRejectedCount() {
        final ManagedJBossThreadPoolExecutorReuseIdleThreadsService executor = getValue();
        return executor.getRejectedCount();
    }

    public long getTaskCount() {
        final ManagedJBossThreadPoolExecutorReuseIdleThreadsService executor = getValue();
        return executor.getTaskCount();
    }

    public int getQueueSize() {
        final ManagedJBossThreadPoolExecutorReuseIdleThreadsService executor = getValue();
        return executor.getQueueSize();
    }

    TimeUnit getKeepAliveUnit() {
        return keepAlive == null ? TimeSpec.DEFAULT_KEEPALIVE.getUnit() : keepAlive.getUnit();
    }
}
