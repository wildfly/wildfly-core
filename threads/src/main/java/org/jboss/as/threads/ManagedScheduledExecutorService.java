/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jboss.threads.JBossExecutors;

/**
 *
 * @author Alexey Loubyansky
 */
public class ManagedScheduledExecutorService extends ManagedExecutorService implements ScheduledExecutorService {

    private final ScheduledThreadPoolExecutor executor;

    public ManagedScheduledExecutorService(ScheduledThreadPoolExecutor executor) {
        super(executor);
        this.executor = executor;
    }

    @Override
    protected ExecutorService protectExecutor(ExecutorService executor) {
        return JBossExecutors.protectedScheduledExecutorService((ScheduledExecutorService) executor);
    }

    @Override
    void internalShutdown() {
        executor.shutdown();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
       return executor.schedule(command, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
       return executor.schedule(callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
       return executor.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
       return executor.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

   public int getActiveCount() {
        return executor.getActiveCount();
    }

    public long getCompletedTaskCount() {
        return executor.getCompletedTaskCount();
    }

    public int getLargestPoolSize() {
        return executor.getLargestPoolSize();
    }

    public int getPoolSize() {
        return executor.getPoolSize();
    }

    public long getTaskCount() {
        return executor.getTaskCount();
    }

    public int getQueueSize() {
        return executor.getQueue().size();
    }
}
