/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;

import org.jboss.threads.EventListener;
import org.jboss.threads.QueueExecutor;

/**
 *
 * @author Alexey Loubyansky
 */
public class ManagedQueueExecutorService extends ManagedExecutorService {

    private final QueueExecutor executor;

    public ManagedQueueExecutorService(QueueExecutor executor) {
        super(executor);
        this.executor = executor;
    }

    @Override
    void internalShutdown() {
        executor.shutdown();
    }

    public int getCoreThreads() {
        return executor.getCoreThreads();
    }

    // Package protected for subsys write-attribute handlers
    void setCoreThreads(int coreThreads) {
        executor.setCoreThreads(coreThreads);
    }

    public boolean isAllowCoreTimeout() {
        return executor.isAllowCoreThreadTimeout();
    }

    void setAllowCoreTimeout(boolean allowCoreTimeout) {
        executor.setAllowCoreThreadTimeout(allowCoreTimeout);
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

    void setKeepAlive(TimeSpec keepAlive) {
        executor.setKeepAliveTime(keepAlive.getDuration(), keepAlive.getUnit());
    }

    public int getCurrentThreadCount() {
        return executor.getCurrentThreadCount();
    }

    public int getLargestThreadCount() {
        return executor.getLargestThreadCount();
    }

    public int getRejectedCount() {
        return executor.getRejectedCount();
    }

    public int getQueueSize() {
        return executor.getQueueSize();
    }

    <A> void addShutdownListener(final EventListener<A> shutdownListener, final A attachment) {
        executor.addShutdownListener(shutdownListener, attachment);
    }
}
