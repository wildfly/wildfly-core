/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;

import javax.management.ObjectName;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.platform.mbean.logging.PlatformMBeanLogger;
import org.jboss.dmr.ModelNode;

/**
 * Handles read-attribute and write-attribute for the resource representing {@code java.lang.management.BufferPoolMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class BufferPoolMXBeanAttributeHandler extends AbstractPlatformMBeanAttributeHandler {

    static final BufferPoolMXBeanAttributeHandler INSTANCE = new BufferPoolMXBeanAttributeHandler();

    private BufferPoolMXBeanAttributeHandler() {
    }

    @Override
    protected void executeReadAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String bpName = context.getCurrentAddressValue();

        ObjectName objectName = PlatformMBeanUtil.getObjectNameWithNameKey(PlatformMBeanConstants.BUFFER_POOL_MXBEAN_DOMAIN_TYPE, bpName);
        if (!ManagementFactory.getPlatformMBeanServer().isRegistered(objectName)) {
            throw PlatformMBeanLogger.ROOT_LOGGER.unknownBufferPool(objectName.getKeyProperty(ModelDescriptionConstants.NAME));
        }

        final String name = operation.require(ModelDescriptionConstants.NAME).asString();

        if (PlatformMBeanConstants.OBJECT_NAME.getName().equals(name)) {
            context.getResult().set(objectName.toString());
        } else if (ModelDescriptionConstants.NAME.equals(name)) {
            context.getResult().set(PlatformMBeanUtil.getMBeanAttribute(objectName, "Name").toString());
        } else if (PlatformMBeanConstants.COUNT.equals(name)) {
            context.getResult().set(Long.class.cast(PlatformMBeanUtil.getMBeanAttribute(objectName, "Count")));
        } else if (PlatformMBeanConstants.MEMORY_USED_NAME.equals(name)) {
            context.getResult().set(Long.class.cast(PlatformMBeanUtil.getMBeanAttribute(objectName, "MemoryUsed")));
        } else if (PlatformMBeanConstants.TOTAL_CAPACITY.equals(name)) {
            context.getResult().set(Long.class.cast(PlatformMBeanUtil.getMBeanAttribute(objectName, "TotalCapacity")));
        } else if (BufferPoolResourceDefinition.BUFFER_POOL_METRICS.contains(name)) {
            // Bug
            throw PlatformMBeanLogger.ROOT_LOGGER.badReadAttributeImpl(name);
        } else {
            // Shouldn't happen; the global handler should reject
            throw unknownAttribute(operation);
        }

    }

    @Override
    protected void executeWriteAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        // Shouldn't happen; the global handler should reject
        throw unknownAttribute(operation);

    }
}
