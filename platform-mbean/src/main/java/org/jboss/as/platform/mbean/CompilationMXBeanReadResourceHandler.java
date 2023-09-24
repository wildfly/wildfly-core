/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.dmr.ModelNode;

/**
 * Handles read-resource for the resource representing {@link java.lang.management.CompilationMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class CompilationMXBeanReadResourceHandler implements OperationStepHandler {

    public static final CompilationMXBeanReadResourceHandler INSTANCE = new CompilationMXBeanReadResourceHandler();

    private CompilationMXBeanReadResourceHandler() {
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final ModelNode result = context.getResult();

        for (String attribute : CompilationResourceDefinition.COMPILATION_READ_ATTRIBUTES) {
            final ModelNode store = result.get(attribute);
            try {
                CompilationMXBeanAttributeHandler.storeResult(attribute, store);
            } catch (UnsupportedOperationException ignored) {
                // just leave it undefined
            }
        }

        for (String attribute : CompilationResourceDefinition.COMPILATION_METRICS) {
            final ModelNode store = result.get(attribute);
            try {
                CompilationMXBeanAttributeHandler.storeResult(attribute, store);
            } catch (UnsupportedOperationException ignored) {
                // just leave it undefined
            }
        }

        final ModelNode store = result.get(PlatformMBeanConstants.OBJECT_NAME.getName());
        CompilationMXBeanAttributeHandler.storeResult(PlatformMBeanConstants.OBJECT_NAME.getName(), store);
    }
}
