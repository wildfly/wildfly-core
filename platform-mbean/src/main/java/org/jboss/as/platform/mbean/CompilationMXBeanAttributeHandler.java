/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import static org.jboss.as.platform.mbean.CompilationResourceDefinition.COMPILATION_METRICS;
import static org.jboss.as.platform.mbean.CompilationResourceDefinition.COMPILATION_READ_ATTRIBUTES;

import java.lang.management.ManagementFactory;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.platform.mbean.logging.PlatformMBeanLogger;
import org.jboss.dmr.ModelNode;

/**
 * Handles read-attribute and write-attribute for the resource representing {@link java.lang.management.CompilationMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class CompilationMXBeanAttributeHandler extends AbstractPlatformMBeanAttributeHandler {

    static final CompilationMXBeanAttributeHandler INSTANCE = new CompilationMXBeanAttributeHandler();

    private CompilationMXBeanAttributeHandler() {

    }

    @Override
    protected void executeReadAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String name = operation.require(ModelDescriptionConstants.NAME).asString();

        try {
            if ((PlatformMBeanConstants.OBJECT_NAME.getName().equals(name))
                    || COMPILATION_READ_ATTRIBUTES.contains(name)
                    || COMPILATION_METRICS.contains(name)) {
                storeResult(name, context.getResult());
            } else {
                // Shouldn't happen; the global handler should reject
                throw unknownAttribute(operation);
            }
        } catch (UnsupportedOperationException e) {
            throw new OperationFailedException(e.toString());
        }

    }

    static void storeResult(final String attributeName, final ModelNode store) throws OperationFailedException {
        if (PlatformMBeanConstants.OBJECT_NAME.getName().equals(attributeName)) {
            store.set(ManagementFactory.COMPILATION_MXBEAN_NAME);
        } else if (ModelDescriptionConstants.NAME.equals(attributeName)) {
            store.set(ManagementFactory.getCompilationMXBean().getName());
        } else if (PlatformMBeanConstants.COMPILATION_TIME_MONITORING_SUPPORTED.equals(attributeName)) {
            store.set(ManagementFactory.getCompilationMXBean().isCompilationTimeMonitoringSupported());
        } else if (PlatformMBeanConstants.TOTAL_COMPILATION_TIME.equals(attributeName)) {
            store.set(ManagementFactory.getCompilationMXBean().getTotalCompilationTime());
        } else {
            if (COMPILATION_READ_ATTRIBUTES.contains(attributeName)|| COMPILATION_METRICS.contains(attributeName)) {
                // Bug
                throw PlatformMBeanLogger.ROOT_LOGGER.badReadAttributeImpl(attributeName);
            }
        }
    }

    @Override
    protected void executeWriteAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        // Shouldn't happen; the global handler should reject
        throw unknownAttribute(operation);

    }
}
