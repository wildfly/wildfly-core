/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.jboss.threads.BlockingExecutor;
import org.jboss.threads.EventListener;
import org.jboss.threads.JBossExecutors;
import org.jboss.threads.QueuelessExecutor;

/**
 *
 * @author Alexey Loubyansky
 */
public class ManagedQueuelessExecutorService extends ManagedExecutorService implements BlockingExecutor {

    private final QueuelessExecutor executor;

    public ManagedQueuelessExecutorService(QueuelessExecutor executor) {
        super(executor);
        this.executor = executor;
    }

    @Override
    protected ExecutorService protectExecutor(ExecutorService executor) {
        return JBossExecutors.protectedBlockingExecutorService((BlockingExecutor) executor);
    }

    @Override
    void internalShutdown() {
        executor.shutdown();
    }

    public boolean isBlocking() {
        return executor.isBlocking();
    }

    void setBlocking(boolean blocking) {
        executor.setBlocking(blocking);
    }

    public int getMaxThreads() {
        return executor.getMaxThreads();
    }

    void setMaxThreads(int maxThreads) {
        executor.setMaxThreads(maxThreads);
    }

    public long getKeepAlive() {
        return executor.getKeepAliveTime();
    }

    void setKeepAlive(long milliseconds) {
        executor.setKeepAliveTime(milliseconds);
    }

    public int getRejectedCount() {
        return executor.getRejectedCount();
    }

    public int getCurrentThreadCount() {
        return executor.getCurrentThreadCount();
    }

    public int getLargestThreadCount() {
        return executor.getLargestThreadCount();
    }

    public int getQueueSize(){
        return this.executor.getQueueSize();
    }

    <A> void addShutdownListener(final EventListener<A> shutdownListener, final A attachment) {
        executor.addShutdownListener(shutdownListener, attachment);
    }

    @Override
    public void executeBlocking(Runnable task)
            throws RejectedExecutionException, InterruptedException {
        executor.executeBlocking(task);
    }

    @Override
    public void executeBlocking(Runnable task, long timeout, TimeUnit unit)
            throws RejectedExecutionException, InterruptedException {
        executor.executeBlocking(task, timeout, unit);
    }

    @Override
    public void executeNonBlocking(Runnable task)
            throws RejectedExecutionException {
        executor.executeNonBlocking(task);
    }
}
