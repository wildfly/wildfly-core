/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import static org.jboss.as.platform.mbean.CompilationResourceDefinition.COMPILATION_METRICS;
import static org.jboss.as.platform.mbean.CompilationResourceDefinition.COMPILATION_READ_ATTRIBUTES;

import java.lang.management.CompilationMXBean;
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
                context.getResult().set(getResult(name, ManagementFactory.getCompilationMXBean()));
            } else {
                // Shouldn't happen; the global handler should reject
                throw unknownAttribute(operation);
            }
        } catch (UnsupportedOperationException e) {
            throw new OperationFailedException(e.toString());
        }

    }

    static ModelNode getResult(final String attributeName, final CompilationMXBean mbean) {
        final ModelNode store;
        if (PlatformMBeanConstants.OBJECT_NAME.getName().equals(attributeName)) {
            store = new ModelNode(ManagementFactory.COMPILATION_MXBEAN_NAME);
        } else if (ModelDescriptionConstants.NAME.equals(attributeName)) {
            store = new ModelNode(mbean.getName());
        } else if (PlatformMBeanConstants.COMPILATION_TIME_MONITORING_SUPPORTED.equals(attributeName)) {
            store = new ModelNode(mbean.isCompilationTimeMonitoringSupported());
        } else if (PlatformMBeanConstants.TOTAL_COMPILATION_TIME.equals(attributeName)) {
            store = new ModelNode(mbean.getTotalCompilationTime());
        } else if (COMPILATION_READ_ATTRIBUTES.contains(attributeName)|| COMPILATION_METRICS.contains(attributeName)) {
                // Bug
                throw PlatformMBeanLogger.ROOT_LOGGER.badReadAttributeImpl(attributeName);
        } else {
            // TODO should not happen and we should fail, but historically this would have resulted
            //  in an undefined node, so we keep it that way
            // throw new IllegalArgumentException(attributeName);
            store = new ModelNode();
        }
        return store;
    }

    @Override
    protected void executeWriteAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        // Shouldn't happen; the global handler should reject
        throw unknownAttribute(operation);

    }
}
