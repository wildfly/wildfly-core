/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.platform.mbean.logging.PlatformMBeanLogger;
import org.jboss.dmr.ModelNode;

/**
 * Handles read-attribute and write-attribute for the resource representing {@link java.lang.management.OperatingSystemMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class OperatingSystemMXBeanAttributeHandler extends AbstractPlatformMBeanAttributeHandler {

    static final OperatingSystemMXBeanAttributeHandler INSTANCE = new OperatingSystemMXBeanAttributeHandler();

    private OperatingSystemMXBeanAttributeHandler() {

    }

    @Override
    protected void executeReadAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String name = operation.require(ModelDescriptionConstants.NAME).asString();

        try {
            if ((PlatformMBeanConstants.OBJECT_NAME.getName().equals(name))
                    || OperatingSystemResourceDefinition.OPERATING_SYSTEM_READ_ATTRIBUTES.contains(name)
                    || OperatingSystemResourceDefinition.OPERATING_SYSTEM_METRICS.contains(name)) {
                storeResult(name, context.getResult());
            } else {
                if (OperatingSystemResourceDefinition.OPERATING_SYSTEM_EXTENDED_METRICS.contains(name)) {
                    storeExtendedResult(name, context.getResult());
                } else {
                    // Shouldn't happen; the global handler should reject
                    throw unknownAttribute(operation);
                }
            }
        } catch (SecurityException e) {
            throw new OperationFailedException(e.toString());
        }

    }

    @Override
    protected void executeWriteAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {
        // Shouldn't happen; the global handler should reject
        throw unknownAttribute(operation);

    }

    static void storeExtendedResult(String name, ModelNode store) {
        ExtendedOperatingSystemMBean ext = new ExtendedOperatingSystemMBean();
        if (PlatformMBeanConstants.COMMITTED_VIRTUAL_MEMORY_SIZE.equals(name)) {
            if (ext.isAttributeDefined(ExtendedOperatingSystemMBean.COMMITTED_VIRTUAL_MEMORY_SIZE_ATTRIBUTE)) {
                store.set(ext.getCommittedVirtualMemorySize());
            }
        } else if (PlatformMBeanConstants.FREE_MEMORY_SIZE.equals(name)) {
            if (ext.isAttributeDefined(ExtendedOperatingSystemMBean.FREE_MEMORY_SIZE_ATTRIBUTE)) {
                store.set(ext.getFreePhysicalMemorySize());
            }
        } else if (PlatformMBeanConstants.FREE_SWAP_SPACE_SIZE.equals(name)) {
            if (ext.isAttributeDefined(ExtendedOperatingSystemMBean.FREE_SWAP_SPACE_SIZE_ATTRIBUTE)) {
                store.set(ext.getFreeSwapSpaceSize());
            }
        } else if (PlatformMBeanConstants.PROCESS_CPU_LOAD.equals(name)) {
            if (ext.isAttributeDefined(ExtendedOperatingSystemMBean.PROCESS_CPU_LOAD_ATTRIBUTE)) {
                store.set(ext.getProcessCpuLoad());
            }
        } else if (PlatformMBeanConstants.PROCESS_CPU_TIME.equals(name)) {
            if (ext.isAttributeDefined(ExtendedOperatingSystemMBean.PROCESS_CPU_TIME_ATTRIBUTE)) {
                store.set(ext.getProcessCpuTime());
            }
        } else if (PlatformMBeanConstants.CPU_LOAD.equals(name)) {
            if (ext.isAttributeDefined(ExtendedOperatingSystemMBean.CPU_LOAD_ATTRIBUTE)) {
                store.set(ext.getSystemCpuLoad());
            }
        } else if (PlatformMBeanConstants.TOTAL_MEMORY_SIZE.equals(name)) {
            if (ext.isAttributeDefined(ExtendedOperatingSystemMBean.TOTAL_MEMORY_SIZE)) {
                store.set(ext.getTotalPhysicalMemorySize());
            }
        } else if (PlatformMBeanConstants.TOTAL_SWAP_SPACE_SIZE.equals(name)) {
            if (ext.isAttributeDefined(ExtendedOperatingSystemMBean.TOTAL_SWAP_SPACE_SIZE)) {
                store.set(ext.getTotalSwapSpaceSize());
            }
        } else if (PlatformMBeanConstants.MAX_FILE_DESCRIPTOR_COUNT.equals(name)) {
            if (ext.isAttributeDefined(ExtendedOperatingSystemMBean.MAX_FILE_DESCRIPTOR_COUNT_ATTRIBUTE)) {
                store.set(ext.getMaxFileDescriptorCount());
            }
        } else if (PlatformMBeanConstants.OPEN_FILE_DESCRIPTOR_COUNT.equals(name)) {
            if (ext.isAttributeDefined(ExtendedOperatingSystemMBean.OPEN_FILE_DESCRIPTOR_COUNT)) {
                store.set(ext.getOpenFileDescriptorCount());
            }
        } else if (OperatingSystemResourceDefinition.OPERATING_SYSTEM_EXTENDED_METRICS.contains(name)) {
            // Bug
            throw PlatformMBeanLogger.ROOT_LOGGER.badReadAttributeImpl(name);
        }
    }

    static void storeResult(final String name, final ModelNode store) {

        if (PlatformMBeanConstants.OBJECT_NAME.getName().equals(name)) {
            store.set(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
        } else if (ModelDescriptionConstants.NAME.equals(name)) {
            store.set(ManagementFactory.getOperatingSystemMXBean().getName());
        } else if (PlatformMBeanConstants.ARCH.equals(name)) {
            store.set(ManagementFactory.getOperatingSystemMXBean().getArch());
        } else if (PlatformMBeanConstants.VERSION.equals(name)) {
            store.set(ManagementFactory.getOperatingSystemMXBean().getVersion());
        } else if (PlatformMBeanConstants.AVAILABLE_PROCESSORS.equals(name)) {
            store.set(ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors());
        } else if (PlatformMBeanConstants.SYSTEM_LOAD_AVERAGE.equals(name)) {
            store.set(ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage());
        } else if (OperatingSystemResourceDefinition.OPERATING_SYSTEM_READ_ATTRIBUTES.contains(name)
                || OperatingSystemResourceDefinition.OPERATING_SYSTEM_METRICS.contains(name)) {
            // Bug
            throw PlatformMBeanLogger.ROOT_LOGGER.badReadAttributeImpl(name);
        }
    }
}
