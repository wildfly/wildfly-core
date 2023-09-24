/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;

import java.util.concurrent.TimeUnit;

import org.jboss.threads.EnhancedQueueExecutor;
import org.jboss.threads.EventListener;
import org.jboss.threads.SimpleShutdownListenable;

class ManagedEnhancedQueueExecutor extends ManagedExecutorService {
    private final EnhancedQueueExecutor executor;
    private final SimpleShutdownListenable shutdownListenable = new SimpleShutdownListenable();

    ManagedEnhancedQueueExecutor(EnhancedQueueExecutor executor) {
        super(executor);
        this.executor = executor;
    }

    @Override
    void internalShutdown() {
        executor.shutdown();
        shutdownListenable.shutdown();
    }

    int getCoreThreads() {
        return executor.getCorePoolSize();
    }

    // Package protected for subsys write-attribute handlers
    void setCoreThreads(int coreThreads) {
        executor.setCorePoolSize(coreThreads);
    }

    boolean isAllowCoreTimeout() {
        return executor.allowsCoreThreadTimeOut();
    }

    void setAllowCoreTimeout(boolean allowCoreTimeout) {
        executor.allowCoreThreadTimeOut(allowCoreTimeout);
    }

    int getMaxThreads() {
        return executor.getMaximumPoolSize();
    }

    void setMaxThreads(int maxThreads) {
        executor.setMaximumPoolSize(maxThreads);
    }

    long getKeepAlive() {
        return executor.getKeepAliveTime(TimeUnit.MILLISECONDS);
    }

    void setKeepAlive(TimeSpec keepAlive) {
        executor.setKeepAliveTime(keepAlive.getDuration(), keepAlive.getUnit());
    }

    int getRejectedCount() {
        return (int) executor.getRejectedTaskCount();
    }

    long getTaskCount() {
        return executor.getSubmittedTaskCount();
    }

    int getLargestThreadCount() {
        return executor.getLargestPoolSize();
    }

    int getLargestPoolSize() {
        return executor.getLargestPoolSize();
    }

    int getCurrentThreadCount() {
        return executor.getPoolSize();
    }

    long getCompletedTaskCount() {
        return executor.getCompletedTaskCount();
    }

    int getActiveCount() {
        return executor.getActiveCount();
    }

    int getQueueSize() {
        return executor.getQueueSize();
    }

    <A> void addShutdownListener(final EventListener<A> shutdownListener, final A attachment) {
        shutdownListenable.addShutdownListener(shutdownListener, attachment);
    }
}
