/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;

/**
 * Resource for the root platform mbean resource tree.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class RootPlatformMBeanResource extends AbstractPlatformMBeanResource {

    public RootPlatformMBeanResource() {
        super(PlatformMBeanConstants.ROOT_PATH);
    }

    @Override
    public Set<String> getChildTypes() {
        return Collections.singleton(ModelDescriptionConstants.TYPE);
    }

    @Override
    Set<String> getChildrenNames() {
        return new LinkedHashSet<String>(PlatformMBeanConstants.BASE_TYPES);
    }

    ResourceEntry getChildEntry(String name) {

        if (PlatformMBeanConstants.CLASS_LOADING.equals(name)) {
            return new LeafPlatformMBeanResource(PlatformMBeanConstants.CLASS_LOADING_PATH);
        } else if (PlatformMBeanConstants.COMPILATION.equals(name) && ManagementFactory.getCompilationMXBean() != null) {
            return new LeafPlatformMBeanResource(PlatformMBeanConstants.COMPILATION_PATH);
        } else if (PlatformMBeanConstants.GARBAGE_COLLECTOR.equals(name)) {
            return new GarbageCollectorMXBeanResource();
        } else if (PlatformMBeanConstants.MEMORY.equals(name)) {
            return new LeafPlatformMBeanResource(PlatformMBeanConstants.MEMORY_PATH);
        } else if (PlatformMBeanConstants.MEMORY_MANAGER.equals(name)) {
            return new MemoryManagerMXBeanResource();
        } else if (PlatformMBeanConstants.MEMORY_POOL.equals(name)) {
            return new MemoryPoolMXBeanResource();
        } else if (PlatformMBeanConstants.OPERATING_SYSTEM.equals(name)) {
            return new LeafPlatformMBeanResource(PlatformMBeanConstants.OPERATING_SYSTEM_PATH);
        } else if (PlatformMBeanConstants.RUNTIME.equals(name)) {
            return new LeafPlatformMBeanResource(PlatformMBeanConstants.RUNTIME_PATH);
        } else if (PlatformMBeanConstants.THREADING.equals(name)) {
            return new LeafPlatformMBeanResource(PlatformMBeanConstants.THREADING_PATH);
        } else if (PlatformMBeanConstants.BUFFER_POOL.equals(name)) {
            return new BufferPoolMXBeanResource();
        } else if (PlatformMBeanConstants.LOGGING.equals(name)) {
            return new LeafPlatformMBeanResource(PlatformMBeanConstants.LOGGING_PATH);
        } else if (PlatformMBeanConstants.PLATFORM_LOGGING.equals(name)) {
            return new LeafPlatformMBeanResource(PlatformMBeanConstants.PLATFORM_LOGGING_PATH);
        } else {
            return null;
        }
    }
}
