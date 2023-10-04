/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import java.security.PrivilegedAction;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.threads.JBossThreadFactory;

import static java.security.AccessController.doPrivileged;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ThreadFactoryService implements Service<ThreadFactory> {

    private static final Map<String, ThreadGroup> THREAD_GROUP_CACHE = new ConcurrentHashMap<>();

    private String threadGroupName;
    private Integer priority;
    private String namePattern;
    private ThreadFactory value;

    public synchronized String getThreadGroupName() {
        return threadGroupName;
    }

    public synchronized void setThreadGroupName(final String threadGroupName) {
        this.threadGroupName = threadGroupName;
    }

    public synchronized Integer getPriority() {
        return priority;
    }

    public synchronized void setPriority(final Integer priority) {
        this.priority = priority;
    }

    public synchronized String getNamePattern() {
        return namePattern;
    }

    public synchronized void setNamePattern(final String namePattern) {
        this.namePattern = namePattern;
    }

    @Override
    public synchronized void start(final StartContext context) throws StartException {
        final ThreadGroup threadGroup = THREAD_GROUP_CACHE.computeIfAbsent(threadGroupName, name ->
                name != null ? new ThreadGroup(name) : null);
        value = doPrivileged(new PrivilegedAction<ThreadFactory>() {
            public ThreadFactory run() {
                return new JBossThreadFactory(threadGroup, Boolean.FALSE, priority, namePattern, null, null);
            }
        });
    }

    @Override
    public synchronized void stop(final StopContext context) {
        value = null;
    }

    @Override
    public synchronized ThreadFactory getValue() throws IllegalStateException {
        final ThreadFactory value = this.value;
        if (value == null) {
            throw ThreadsLogger.ROOT_LOGGER.threadFactoryUninitialized();
        }
        return value;
    }
}
