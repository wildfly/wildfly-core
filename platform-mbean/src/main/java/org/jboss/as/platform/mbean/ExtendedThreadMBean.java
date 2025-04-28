/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;

/**
 * Implements the access to the com.sun.management.ThreadMXBean
 *
 * @author jdenise
 */
class ExtendedThreadMBean extends AbstractExtendedMBean {

    static final String THREAD_ALLOCATED_MEMORY_ENABLED_ATTRIBUTE = "ThreadAllocatedMemoryEnabled";
    static final String THREAD_ALLOCATED_MEMORY_SUPPORTED_ATTRIBUTE = "ThreadAllocatedMemorySupported";
    static final String CURRENT_THREAD_ALLOCATED_BYTES_ATTRIBUTE = "CurrentThreadAllocatedBytes";

    static final String GET_THREAD_CPU_TIME = "getThreadCpuTime";
    static final String GET_THREAD_ALLOCATED_BYTES = "getThreadAllocatedBytes";
    static final String GET_THREAD_USER_TIME = "getThreadUserTime";

    ExtendedThreadMBean() {
        super(ManagementFactory.THREAD_MXBEAN_NAME);
    }

    long getCurrentThreadAllocatedBytes() {
        return (long) getAttribute(CURRENT_THREAD_ALLOCATED_BYTES_ATTRIBUTE);
    }

    boolean isThreadAllocatedMemoryEnabled() {
        return (boolean) getAttribute(THREAD_ALLOCATED_MEMORY_ENABLED_ATTRIBUTE);

    }

    boolean isThreadAllocatedMemorySupported() {
        return (boolean) getAttribute(THREAD_ALLOCATED_MEMORY_SUPPORTED_ATTRIBUTE);

    }

    void setThreadAllocatedMemoryEnabled(boolean value) {
        setAttribute(THREAD_ALLOCATED_MEMORY_ENABLED_ATTRIBUTE, value);
    }

    long[] getThreadCpuTime(long[] ids) {
        return (long[]) invokeOperation(GET_THREAD_CPU_TIME,
                new Object[]{ids}, new String[]{long[].class.getName()});
    }

    long getThreadAllocatedBytes(long id) {
        return (long) invokeOperation(GET_THREAD_ALLOCATED_BYTES,
                new Object[]{id}, new String[]{long.class.getName()});
    }

    long[] getThreadAllocatedBytes(long[] id) {
        return (long[]) invokeOperation(GET_THREAD_ALLOCATED_BYTES,
                new Object[]{id}, new String[]{long[].class.getName()});
    }

    long[] getThreadUserTime(long[] ids) {
        return (long[]) invokeOperation(GET_THREAD_USER_TIME,
                new Object[]{ids}, new String[]{long[].class.getName()});
    }
}
