/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;

/**
 * Implements the access to the com.sun.management.OperatingSystemMXBean
 *
 * @author jdenise
 */
class ExtendedOperatingSystemMBean extends AbstractExtendedMBean {

    static final String COMMITTED_VIRTUAL_MEMORY_SIZE_ATTRIBUTE = "CommittedVirtualMemorySize";
    static final String FREE_MEMORY_SIZE_ATTRIBUTE = "FreeMemorySize";
    static final String FREE_SWAP_SPACE_SIZE_ATTRIBUTE = "FreeSwapSpaceSize";
    static final String PROCESS_CPU_LOAD_ATTRIBUTE = "ProcessCpuLoad";
    static final String PROCESS_CPU_TIME_ATTRIBUTE = "ProcessCpuTime";
    static final String CPU_LOAD_ATTRIBUTE = "CpuLoad";
    static final String TOTAL_MEMORY_SIZE = "TotalMemorySize";
    static final String TOTAL_SWAP_SPACE_SIZE = "TotalSwapSpaceSize";

    // Unix specific
    static final String MAX_FILE_DESCRIPTOR_COUNT_ATTRIBUTE = "MaxFileDescriptorCount";
    static final String OPEN_FILE_DESCRIPTOR_COUNT = "OpenFileDescriptorCount";

    ExtendedOperatingSystemMBean() {
        super(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
    }

    long getCommittedVirtualMemorySize() {
        return (long) getAttribute(COMMITTED_VIRTUAL_MEMORY_SIZE_ATTRIBUTE);

    }

    long getFreePhysicalMemorySize() {
        return (long) getAttribute(FREE_MEMORY_SIZE_ATTRIBUTE);
    }

    long getFreeSwapSpaceSize() {
        return (long) getAttribute(FREE_SWAP_SPACE_SIZE_ATTRIBUTE);
    }

    double getProcessCpuLoad() {
        return (double) getAttribute(PROCESS_CPU_LOAD_ATTRIBUTE);
    }

    double getSystemCpuLoad() {
        return (double) getAttribute(CPU_LOAD_ATTRIBUTE);
    }

    long getProcessCpuTime() {
        return (long) getAttribute(PROCESS_CPU_TIME_ATTRIBUTE);
    }

    long getTotalPhysicalMemorySize() {
        return (long) getAttribute(TOTAL_MEMORY_SIZE);
    }

    long getTotalSwapSpaceSize() {
        return (long) getAttribute(TOTAL_SWAP_SPACE_SIZE);
    }

    long getMaxFileDescriptorCount() {
        return (long) getAttribute(MAX_FILE_DESCRIPTOR_COUNT_ATTRIBUTE);
    }

    long getOpenFileDescriptorCount() {
        return (long) getAttribute(OPEN_FILE_DESCRIPTOR_COUNT);
    }
}
