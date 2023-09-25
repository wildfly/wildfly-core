/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import static org.jboss.as.platform.mbean.PlatformMBeanUtil.escapeMBeanName;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryManagerMXBean;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.platform.mbean.logging.PlatformMBeanLogger;
import org.jboss.dmr.ModelNode;

/**
 * Handles read-attribute and write-attribute for the resource representing {@link java.lang.management.MemoryManagerMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class MemoryManagerMXBeanAttributeHandler extends AbstractPlatformMBeanAttributeHandler {

    static final MemoryManagerMXBeanAttributeHandler INSTANCE = new MemoryManagerMXBeanAttributeHandler();

    private MemoryManagerMXBeanAttributeHandler() {

    }

    @Override
    protected void executeReadAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String mmName = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR)).getLastElement().getValue();
        final String name = operation.require(ModelDescriptionConstants.NAME).asString();

        MemoryManagerMXBean memoryManagerMXBean = null;

        for (MemoryManagerMXBean mbean : ManagementFactory.getMemoryManagerMXBeans()) {
            if (mmName.equals(escapeMBeanName(mbean.getName()))) {
                memoryManagerMXBean = mbean;
            }
        }

        if (memoryManagerMXBean == null) {
            throw PlatformMBeanLogger.ROOT_LOGGER.unknownMemoryManager(mmName);
        }

        if (PlatformMBeanConstants.OBJECT_NAME.getName().equals(name)) {
            final String objName = PlatformMBeanUtil.getObjectNameStringWithNameKey(ManagementFactory.MEMORY_MANAGER_MXBEAN_DOMAIN_TYPE, mmName);
            context.getResult().set(objName);
        } else if (ModelDescriptionConstants.NAME.equals(name)) {
            context.getResult().set(escapeMBeanName(memoryManagerMXBean.getName()));
        } else if (PlatformMBeanConstants.VALID.getName().equals(name)) {
            context.getResult().set(memoryManagerMXBean.isValid());
        } else if (PlatformMBeanConstants.MEMORY_POOL_NAMES.equals(name)) {
            final ModelNode result = context.getResult();
            result.setEmptyList();
            for (String pool : memoryManagerMXBean.getMemoryPoolNames()) {
                result.add(escapeMBeanName(pool));
            }
        } else if (MemoryManagerResourceDefinition.MEMORY_MANAGER_READ_ATTRIBUTES.contains(name)) {
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
