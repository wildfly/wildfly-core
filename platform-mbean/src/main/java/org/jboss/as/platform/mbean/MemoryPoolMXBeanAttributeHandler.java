/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import static org.jboss.as.platform.mbean.PlatformMBeanConstants.MEMORY_POOL_TYPE;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.TYPE;
import static org.jboss.as.platform.mbean.PlatformMBeanUtil.escapeMBeanName;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.LongRangeValidator;
import org.jboss.as.controller.operations.validation.ParametersValidator;
import org.jboss.as.platform.mbean.logging.PlatformMBeanLogger;
import org.jboss.dmr.ModelNode;

/**
 * Handles read-attribute and write-attribute for the resource representing {@link java.lang.management.MemoryPoolMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class MemoryPoolMXBeanAttributeHandler extends AbstractPlatformMBeanAttributeHandler {

    static final MemoryPoolMXBeanAttributeHandler INSTANCE = new MemoryPoolMXBeanAttributeHandler();

    private final ParametersValidator usageValidator = new ParametersValidator();

    private MemoryPoolMXBeanAttributeHandler() {
        usageValidator.registerValidator(ModelDescriptionConstants.VALUE, LongRangeValidator.NON_NEGATIVE);
    }

    @Override
    protected void executeReadAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String memPoolName = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR)).getLastElement().getValue();
        final String name = operation.require(ModelDescriptionConstants.NAME).asString();

        try {
            if ((PlatformMBeanConstants.OBJECT_NAME.getName().equals(name))
                    || MemoryPoolResourceDefinition.MEMORY_POOL_READ_ATTRIBUTES.contains(name)
                    || MemoryPoolResourceDefinition.MEMORY_POOL_READ_WRITE_ATTRIBUTES.contains(name)
                    || MemoryPoolResourceDefinition.MEMORY_POOL_METRICS.contains(name)) {
                MemoryPoolMXBean memoryPoolMXBean = getMemoryPoolMXBean(memPoolName);
                storeResult(name, context.getResult(), memoryPoolMXBean, memPoolName);
            } else {
                // Shouldn't happen; the global handler should reject
                throw unknownAttribute(operation);
            }
        } catch (UnsupportedOperationException e) {
            throw new OperationFailedException(e.toString());
        }

    }

    @Override
    protected void executeWriteAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String memPoolName = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR)).getLastElement().getValue();
        MemoryPoolMXBean memoryPoolMXBean = getMemoryPoolMXBean(memPoolName);

        final String name = operation.require(ModelDescriptionConstants.NAME).asString();

        try {
            if (PlatformMBeanConstants.USAGE_THRESHOLD.equals(name)) {
                context.getServiceRegistry(true); //to trigger auth
                usageValidator.validate(operation);
                memoryPoolMXBean.setUsageThreshold(operation.require(ModelDescriptionConstants.VALUE).asLong());
            } else if (PlatformMBeanConstants.COLLECTION_USAGE_THRESHOLD.equals(name)) {
                context.getServiceRegistry(true); //to trigger auth
                usageValidator.validate(operation);
                memoryPoolMXBean.setCollectionUsageThreshold(operation.require(ModelDescriptionConstants.VALUE).asLong());
            } else if (MemoryPoolResourceDefinition.MEMORY_POOL_READ_WRITE_ATTRIBUTES.contains(name)) {
                // Bug
                throw PlatformMBeanLogger.ROOT_LOGGER.badWriteAttributeImpl(name);
            } else {
                // Shouldn't happen; the global handler should reject
                throw unknownAttribute(operation);
            }
        } catch (SecurityException e) {
            throw new OperationFailedException(e.toString());
        } catch (UnsupportedOperationException e) {
            throw new OperationFailedException(e.toString());
        }

    }

    static void storeResult(final String name, final ModelNode store, final MemoryPoolMXBean memoryPoolMXBean, final String memPoolName) {

        if (PlatformMBeanConstants.OBJECT_NAME.getName().equals(name)) {
            final String objName = PlatformMBeanUtil.getObjectNameStringWithNameKey(ManagementFactory.MEMORY_POOL_MXBEAN_DOMAIN_TYPE, memPoolName);
            store.set(objName);
        } else if (ModelDescriptionConstants.NAME.equals(name)) {
            store.set(escapeMBeanName(memoryPoolMXBean.getName()));
        } else if (PlatformMBeanConstants.TYPE.equals(name)) {
            store.set(memoryPoolMXBean.getType().name());
        } else if (PlatformMBeanConstants.USAGE.equals(name)) {
            final ModelNode usage = PlatformMBeanUtil.getDetypedMemoryUsage(memoryPoolMXBean.getUsage());
            store.set(usage);
        } else if (PlatformMBeanConstants.PEAK_USAGE.equals(name)) {
            final ModelNode usage = PlatformMBeanUtil.getDetypedMemoryUsage(memoryPoolMXBean.getPeakUsage());
            store.set(usage);
        } else if (PlatformMBeanConstants.VALID.getName().equals(name)) {
            store.set(memoryPoolMXBean.isValid());
        } else if (PlatformMBeanConstants.MEMORY_MANAGER_NAMES.equals(name)) {
            store.setEmptyList();
            for (String mgr : memoryPoolMXBean.getMemoryManagerNames()) {
                store.add(escapeMBeanName(mgr));
            }
        } else if (PlatformMBeanConstants.USAGE_THRESHOLD.equals(name)) {
            store.set(memoryPoolMXBean.getUsageThreshold());
        } else if (PlatformMBeanConstants.USAGE_THRESHOLD_EXCEEDED.equals(name)) {
            store.set(memoryPoolMXBean.isUsageThresholdExceeded());
        } else if (PlatformMBeanConstants.USAGE_THRESHOLD_COUNT.equals(name)) {
            store.set(memoryPoolMXBean.getUsageThresholdCount());
        } else if (PlatformMBeanConstants.USAGE_THRESHOLD_SUPPORTED.equals(name)) {
            store.set(memoryPoolMXBean.isUsageThresholdSupported());
        } else if (PlatformMBeanConstants.COLLECTION_USAGE_THRESHOLD.equals(name)) {
            store.set(memoryPoolMXBean.getCollectionUsageThreshold());
        } else if (PlatformMBeanConstants.COLLECTION_USAGE_THRESHOLD_EXCEEDED.equals(name)) {
            store.set(memoryPoolMXBean.isCollectionUsageThresholdExceeded());
        } else if (PlatformMBeanConstants.COLLECTION_USAGE_THRESHOLD_COUNT.equals(name)) {
            store.set(memoryPoolMXBean.getCollectionUsageThresholdCount());
        } else if (PlatformMBeanConstants.COLLECTION_USAGE_THRESHOLD_SUPPORTED.equals(name)) {
            store.set(memoryPoolMXBean.isCollectionUsageThresholdSupported());
        } else if (PlatformMBeanConstants.COLLECTION_USAGE.equals(name)) {
            final ModelNode usage = PlatformMBeanUtil.getDetypedMemoryUsage(memoryPoolMXBean.getCollectionUsage());
            store.set(usage);
        } else if (MemoryPoolResourceDefinition.MEMORY_POOL_READ_ATTRIBUTES.contains(name)
                || MemoryPoolResourceDefinition.MEMORY_POOL_READ_WRITE_ATTRIBUTES.contains(name)
                || MemoryPoolResourceDefinition.MEMORY_POOL_METRICS.contains(name)) {
            // Bug
            throw PlatformMBeanLogger.ROOT_LOGGER.badReadAttributeImpl(name);
        }

    }

    static MemoryPoolMXBean getMemoryPoolMXBean(String memPoolName) throws OperationFailedException {

        MemoryPoolMXBean memoryPoolMXBean = null;

        for (MemoryPoolMXBean mbean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (memPoolName.equals(escapeMBeanName(mbean.getName())) &&
                mbean.getObjectName().getKeyProperty(TYPE).equals(MEMORY_POOL_TYPE)) {
                memoryPoolMXBean = mbean;
            }
        }

        if (memoryPoolMXBean == null) {
            throw PlatformMBeanLogger.ROOT_LOGGER.unknownMemoryPool(memPoolName);
        }
        return memoryPoolMXBean;
    }
}
