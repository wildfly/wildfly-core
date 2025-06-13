/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.Map;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

/**
 * Implements the access to the com.sun.management.GarbageCollectorMXBean
 *
 * @author jdenise
 */
class ExtendedGarbageCollectorMBean extends AbstractExtendedMBean {

    static class GcInfo {

        private final long duration;
        private final long endTime;
        private final long id;
        private final Map<String, MemoryUsage> memoryUsageAfterGc;
        private final Map<String, MemoryUsage> memoryUsageBeforeGc;
        private final long startTime;

        GcInfo(long duration,
                long endTime,
                long id,
                Map<String, MemoryUsage> memoryUsageAfterGc,
                Map<String, MemoryUsage> memoryUsageBeforeGc,
                long startTime) {
            this.duration = duration;
            this.endTime = endTime;
            this.id = id;
            this.memoryUsageAfterGc = memoryUsageAfterGc;
            this.memoryUsageBeforeGc = memoryUsageBeforeGc;
            this.startTime = startTime;
        }

        long getDuration() {
            return duration;
        }

        long getEndTime() {
            return endTime;
        }

        long getId() {
            return id;
        }

        Map<String, MemoryUsage> getMemoryUsageAfterGc() {
            return memoryUsageAfterGc;
        }

        Map<String, MemoryUsage> getMemoryUsageBeforeGc() {
            return memoryUsageBeforeGc;
        }

        long getStartTime() {
            return startTime;
        }
    }
    static final String LAST_GC_INFO_ATTRIBUTE = "LastGcInfo";
    private static final String DURATION = "duration";
    private static final String END_TIME = "endTime";
    private static final String ID = "id";
    private static final String MEMORY_USAGE_BEFORE_GC = "memoryUsageBeforeGc";
    private static final String MEMORY_USAGE_AFTER_GC = "memoryUsageAfterGc";
    private static final String START_TIME = "startTime";

    ExtendedGarbageCollectorMBean(String gcName) {
        super(PlatformMBeanUtil.getObjectNameStringWithNameKey(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE, gcName));
    }

    GcInfo getLastGcInfo() {
        GcInfo gcInfo = null;
        CompositeData cd = (CompositeData) getAttribute(LAST_GC_INFO_ATTRIBUTE);
        if (cd != null) {
            long duration = (long) cd.get(DURATION);
            long endTime = (long) cd.get(END_TIME);
            long id = (long) cd.get(ID);
            TabularData beforeGc = (TabularData) cd.get(MEMORY_USAGE_BEFORE_GC);
            Map<String, MemoryUsage> beforeGcMap = new HashMap<>();
            for (Object value : beforeGc.values()) {
                CompositeData cdValue = (CompositeData) value;
                String key = (String)cdValue.get("key");
                CompositeData usageCd = (CompositeData)cdValue.get("value");
                MemoryUsage usage = MemoryUsage.from(usageCd);
                beforeGcMap.put(key, usage);
            }
            TabularData afterGc = (TabularData) cd.get(MEMORY_USAGE_AFTER_GC);
            Map<String, MemoryUsage> afterGcMap = new HashMap<>();
            for (Object value : afterGc.values()) {
                CompositeData cdValue = (CompositeData) value;
                String key = (String)cdValue.get("key");
                CompositeData usageCd = (CompositeData)cdValue.get("value");
                MemoryUsage usage = MemoryUsage.from(usageCd);
                afterGcMap.put(key, usage);
            }

            long startTime = (long) cd.get(START_TIME);
            gcInfo = new GcInfo(duration,
                    endTime,
                    id,
                    afterGcMap,
                    beforeGcMap,
                    startTime);
        }
        return gcInfo;
    }
}
