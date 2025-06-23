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
import javax.management.openmbean.InvalidKeyException;
import javax.management.openmbean.TabularData;

import org.jboss.as.platform.mbean.logging.PlatformMBeanLogger;

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
    private static final String INDEX = "index";
    private static final String MEMORY_USAGE_BEFORE_GC = "memoryUsageBeforeGc";
    private static final String MEMORY_USAGE_AFTER_GC = "memoryUsageAfterGc";
    private static final String USAGE_BEFORE_GC = "usageBeforeGc";
    private static final String USAGE_AFTER_GC = "usageAfterGc";
    private static final String START_TIME = "startTime";

    ExtendedGarbageCollectorMBean(String gcName) {
        super(PlatformMBeanUtil.getObjectNameStringWithNameKey(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE, gcName));
    }

    /**
     * Gets information about the last garbage collection
     * @return the info. May return {@code null} if there wasn't one or if there was a problem translating
     *         from the underlying JMX data to our GcInfo type
     */
    GcInfo getLastGcInfo() {
        GcInfo gcInfo = null;
        CompositeData cd = (CompositeData) getAttribute(LAST_GC_INFO_ATTRIBUTE);
        if (cd != null) {
            // Create our GcInfo object from the CompositeData.
            // If any expected key is missing, debug log and then return null.
            // Semeru and Temurin have different CompositeData structures
            // Where we know they differ, check for Semeru first and then
            // assume Temurin, so if neither exist the debug message will reference the
            // presumably more common Temurin key.
            try {
                long id;
                if (cd.containsKey(INDEX)) { // Semeru 17 key
                    id = (long) cd.get(INDEX);
                } else {
                    id = (long) cd.get(ID);
                }
                long endTime = (long) cd.get(END_TIME);
                long startTime = (long) cd.get(START_TIME);
                long duration;
                if (cd.containsKey(DURATION)) { // This key exists in Temurin 17+
                    duration = (long) cd.get(DURATION);
                } else {
                    // Semeru 17 has no duration key; just calculate the duration
                    duration = endTime - startTime;
                }
                TabularData beforeGc;
                if (cd.containsKey(USAGE_BEFORE_GC)) {  // Semeru 17 key
                    beforeGc = (TabularData) cd.get(USAGE_BEFORE_GC);
                } else {
                    beforeGc = (TabularData) cd.get(MEMORY_USAGE_BEFORE_GC);
                }
                Map<String, MemoryUsage> beforeGcMap = new HashMap<>();
                for (Object value : beforeGc.values()) {
                    CompositeData cdValue = (CompositeData) value;
                    String key = (String) cdValue.get("key");
                    CompositeData usageCd = (CompositeData) cdValue.get("value");
                    MemoryUsage usage = MemoryUsage.from(usageCd);
                    beforeGcMap.put(key, usage);
                }
                TabularData afterGc;
                if (cd.containsKey(USAGE_AFTER_GC)) {  // Semeru 17 key
                    afterGc = (TabularData) cd.get(USAGE_AFTER_GC);
                } else {
                    afterGc = (TabularData) cd.get(MEMORY_USAGE_AFTER_GC);
                }
                Map<String, MemoryUsage> afterGcMap = new HashMap<>();
                for (Object value : afterGc.values()) {
                    CompositeData cdValue = (CompositeData) value;
                    String key = (String) cdValue.get("key");
                    CompositeData usageCd = (CompositeData) cdValue.get("value");
                    MemoryUsage usage = MemoryUsage.from(usageCd);
                    afterGcMap.put(key, usage);
                }

                gcInfo = new GcInfo(duration,
                        endTime,
                        id,
                        afterGcMap,
                        beforeGcMap,
                        startTime);
            } catch (InvalidKeyException ike) {
                PlatformMBeanLogger.ROOT_LOGGER.debugf(ike,
                        "CompositeData %s has an unexpected structure; unable to create GcInfo", cd);
            }
        }
        return gcInfo;
    }
}
