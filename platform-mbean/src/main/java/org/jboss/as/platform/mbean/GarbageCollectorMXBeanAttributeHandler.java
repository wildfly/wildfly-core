/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import static org.jboss.as.platform.mbean.PlatformMBeanUtil.escapeMBeanName;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.platform.mbean.ExtendedGarbageCollectorMBean.GcInfo;
import org.jboss.as.platform.mbean.logging.PlatformMBeanLogger;
import org.jboss.dmr.ModelNode;

/**
 * Handles read-attribute and write-attribute for the resource representing {@link java.lang.management.GarbageCollectorMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class GarbageCollectorMXBeanAttributeHandler extends AbstractPlatformMBeanAttributeHandler {

    static final GarbageCollectorMXBeanAttributeHandler INSTANCE = new GarbageCollectorMXBeanAttributeHandler();


    private GarbageCollectorMXBeanAttributeHandler() {

    }

    @Override
    protected void executeReadAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String gcName = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR)).getLastElement().getValue();
        final String name = operation.require(ModelDescriptionConstants.NAME).asString();

        GarbageCollectorMXBean gcMBean = null;

        for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gcName.equals(escapeMBeanName(mbean.getName()))) {
                gcMBean = mbean;
            }
        }

        if (gcMBean == null) {
            throw PlatformMBeanLogger.ROOT_LOGGER.unknownGarbageCollector(gcName);
        }
        if (GarbageCollectorResourceDefinition.GARBAGE_COLLECTOR_EXTENDED_READ_ATTRIBUTES.contains(name)) {
            storeExtendedResult(gcName, name, context.getResult());
        } else {
            if (PlatformMBeanConstants.OBJECT_NAME.getName().equals(name)) {
                final String objName = PlatformMBeanUtil.getObjectNameStringWithNameKey(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE, gcName);
                context.getResult().set(objName);
            } else if (ModelDescriptionConstants.NAME.equals(name)) {
                context.getResult().set(escapeMBeanName(gcMBean.getName()));
            } else if (PlatformMBeanConstants.VALID.getName().equals(name)) {
                context.getResult().set(gcMBean.isValid());
            } else if (PlatformMBeanConstants.MEMORY_POOL_NAMES.equals(name)) {
                final ModelNode result = context.getResult();
                result.setEmptyList();
                for (String pool : gcMBean.getMemoryPoolNames()) {
                    result.add(escapeMBeanName(pool));
                }
            } else if (PlatformMBeanConstants.COLLECTION_COUNT.equals(name)) {
                context.getResult().set(gcMBean.getCollectionCount());
            } else if (PlatformMBeanConstants.COLLECTION_TIME.equals(name)) {
                context.getResult().set(gcMBean.getCollectionTime());
            } else if (GarbageCollectorResourceDefinition.GARBAGE_COLLECTOR_READ_ATTRIBUTES.contains(name)
                    || GarbageCollectorResourceDefinition.GARBAGE_COLLECTOR_METRICS.contains(name)) {
                // Bug
                throw PlatformMBeanLogger.ROOT_LOGGER.badReadAttributeImpl(name);
            } else {
                // Shouldn't happen; the global handler should reject
                throw unknownAttribute(operation);
            }
        }
    }

    static void storeExtendedResult(String gcName, String name, ModelNode store) {
        ExtendedGarbageCollectorMBean mbean = new ExtendedGarbageCollectorMBean(gcName);
        if (PlatformMBeanConstants.LAST_GC_INFO.equals(name)) {
            if (mbean.isAttributeDefined(ExtendedGarbageCollectorMBean.LAST_GC_INFO_ATTRIBUTE)) {
                GcInfo info = mbean.getLastGcInfo();
                if (info != null) {
                    store.set(PlatformMBeanUtil.getDetypedGcInfo(info));
                }
            }
        } else if (GarbageCollectorResourceDefinition.GARBAGE_COLLECTOR_EXTENDED_READ_ATTRIBUTES.contains(name)) {
            // Bug
            throw PlatformMBeanLogger.ROOT_LOGGER.badReadAttributeImpl(name);
        }
    }

    @Override
    protected void executeWriteAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        // Shouldn't happen; the global handler should reject
        throw unknownAttribute(operation);

    }
}
