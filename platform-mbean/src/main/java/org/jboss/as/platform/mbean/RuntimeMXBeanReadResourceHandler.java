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
 * Handles read-resource for the resource representing {@link java.lang.management.RuntimeMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class RuntimeMXBeanReadResourceHandler implements OperationStepHandler {

    public static final RuntimeMXBeanReadResourceHandler INSTANCE = new RuntimeMXBeanReadResourceHandler();

    private RuntimeMXBeanReadResourceHandler() {
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final ModelNode result = context.getResult();

        for (String attribute : RuntimeResourceDefinition.RUNTIME_READ_ATTRIBUTES) {
            final ModelNode store = result.get(attribute);
            try {
                RuntimeMXBeanAttributeHandler.storeResult(attribute, store);
            } catch (SecurityException | UnsupportedOperationException ignored) {
                // just leave it undefined
            }
        }

        for (String attribute : RuntimeResourceDefinition.RUNTIME_METRICS) {
            final ModelNode store = result.get(attribute);
            try {
                RuntimeMXBeanAttributeHandler.storeResult(attribute, store);
            } catch (SecurityException | UnsupportedOperationException ignored) {
                // just leave it undefined
            }
        }
        final ModelNode store = result.get(PlatformMBeanConstants.OBJECT_NAME.getName());
        RuntimeMXBeanAttributeHandler.storeResult(PlatformMBeanConstants.OBJECT_NAME.getName(), store);
    }
}
