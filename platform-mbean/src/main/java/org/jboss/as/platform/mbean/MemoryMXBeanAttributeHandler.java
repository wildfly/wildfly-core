/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.platform.mbean.logging.PlatformMBeanLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Handles read-attribute and write-attribute for the resource representing {@link java.lang.management.CompilationMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class MemoryMXBeanAttributeHandler extends AbstractPlatformMBeanAttributeHandler {

    static final MemoryMXBeanAttributeHandler INSTANCE = new MemoryMXBeanAttributeHandler();

    private MemoryMXBeanAttributeHandler() {
        writeAttributeValidator.registerValidator(ModelDescriptionConstants.VALUE, new ModelTypeValidator(ModelType.BOOLEAN));
    }

    @Override
    protected void executeReadAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String name = operation.require(ModelDescriptionConstants.NAME).asString();

        if (PlatformMBeanConstants.OBJECT_NAME.getName().equals(name)) {
            context.getResult().set(ManagementFactory.MEMORY_MXBEAN_NAME);
        } else if (PlatformMBeanConstants.OBJECT_PENDING_FINALIZATION_COUNT.equals(name)) {
            context.getResult().set(ManagementFactory.getMemoryMXBean().getObjectPendingFinalizationCount());
        } else if (PlatformMBeanConstants.HEAP_MEMORY_USAGE.equals(name)) {
            final ModelNode mu = PlatformMBeanUtil.getDetypedMemoryUsage(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage());
            context.getResult().set(mu);
        } else if (PlatformMBeanConstants.NON_HEAP_MEMORY_USAGE.equals(name)) {
            final ModelNode mu = PlatformMBeanUtil.getDetypedMemoryUsage(ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage());
            context.getResult().set(mu);
        } else if (PlatformMBeanConstants.VERBOSE.equals(name)) {
            context.getResult().set(ManagementFactory.getMemoryMXBean().isVerbose());
        } else if (MemoryResourceDefinition.MEMORY_METRICS.contains(name)
                || MemoryResourceDefinition.MEMORY_READ_WRITE_ATTRIBUTES.contains(name)) {
            // Bug
            throw PlatformMBeanLogger.ROOT_LOGGER.badReadAttributeImpl(name);
        } else {
            // Shouldn't happen; the global handler should reject
            throw unknownAttribute(operation);
        }

    }

    @Override
    protected void executeWriteAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String name = operation.require(ModelDescriptionConstants.NAME).asString();
        if (PlatformMBeanConstants.VERBOSE.equals(name)) {
            context.getServiceRegistry(true); //to trigger auth
            ManagementFactory.getMemoryMXBean().setVerbose(operation.require(ModelDescriptionConstants.VALUE).asBoolean());
        } else if (MemoryResourceDefinition.MEMORY_READ_WRITE_ATTRIBUTES.contains(name)) {
            // Bug
            throw PlatformMBeanLogger.ROOT_LOGGER.badWriteAttributeImpl(name);
        } else {
            // Shouldn't happen; the global handler should reject
            throw unknownAttribute(operation);
        }

    }
}
