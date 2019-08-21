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
