/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.MemoryPoolMXBean;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;

/**
 * Handles read-resource for the resource representing {@link java.lang.management.MemoryPoolMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class MemoryPoolMXBeanReadResourceHandler implements OperationStepHandler {

    public static final MemoryPoolMXBeanReadResourceHandler INSTANCE = new MemoryPoolMXBeanReadResourceHandler();

    private MemoryPoolMXBeanReadResourceHandler() {
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String memPoolName = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR)).getLastElement().getValue();

        MemoryPoolMXBean memoryPoolMXBean = MemoryPoolMXBeanAttributeHandler.getMemoryPoolMXBean(memPoolName);

        final ModelNode result = context.getResult();

        for (String attribute : MemoryPoolResourceDefinition.MEMORY_POOL_READ_ATTRIBUTES) {
            final ModelNode store = result.get(attribute);
            try {
                MemoryPoolMXBeanAttributeHandler.storeResult(attribute, store, memoryPoolMXBean, memPoolName);
            } catch (UnsupportedOperationException ignored) {
                // just leave it undefined
            }
        }

        for (String attribute : MemoryPoolResourceDefinition.MEMORY_POOL_READ_WRITE_ATTRIBUTES) {
            final ModelNode store = result.get(attribute);
            try {
                MemoryPoolMXBeanAttributeHandler.storeResult(attribute, store, memoryPoolMXBean, memPoolName);
            } catch (UnsupportedOperationException ignored) {
                // just leave it undefined
            }
        }

        for (String attribute : MemoryPoolResourceDefinition.MEMORY_POOL_METRICS) {
            final ModelNode store = result.get(attribute);
            try {
                MemoryPoolMXBeanAttributeHandler.storeResult(attribute, store, memoryPoolMXBean, memPoolName);
            } catch (UnsupportedOperationException ignored) {
                // just leave it undefined
            }
        }
        final ModelNode store = result.get(PlatformMBeanConstants.OBJECT_NAME.getName());
        MemoryPoolMXBeanAttributeHandler.storeResult(PlatformMBeanConstants.OBJECT_NAME.getName(), store, memoryPoolMXBean, memPoolName);
    }
}
