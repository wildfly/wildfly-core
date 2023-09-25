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
 * Handles read-resource for the resource representing {@link java.lang.management.ThreadMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ThreadMXBeanReadResourceHandler implements OperationStepHandler {

    public static final ThreadMXBeanReadResourceHandler INSTANCE = new ThreadMXBeanReadResourceHandler();

    private ThreadMXBeanReadResourceHandler() {
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final ModelNode result = context.getResult();

        for (String attribute : ThreadResourceDefinition.THREADING_READ_ATTRIBUTES) {
            final ModelNode store = result.get(attribute);
            try {
                ThreadMXBeanAttributeHandler.storeResult(attribute, store);
            } catch (SecurityException ignored) {
                // just leave it undefined
            } catch (UnsupportedOperationException ignored) {
                // just leave it undefined
            }
        }

        for (String attribute : ThreadResourceDefinition.THREADING_READ_WRITE_ATTRIBUTES) {
            final ModelNode store = result.get(attribute);
            try {
                ThreadMXBeanAttributeHandler.storeResult(attribute, store);
            } catch (SecurityException ignored) {
                // just leave it undefined
            } catch (UnsupportedOperationException ignored) {
                // just leave it undefined
            }
        }

        for (String attribute : ThreadResourceDefinition.THREADING_METRICS) {
            final ModelNode store = result.get(attribute);
            try {
                ThreadMXBeanAttributeHandler.storeResult(attribute, store);
            } catch (SecurityException ignored) {
                // just leave it undefined
            } catch (UnsupportedOperationException ignored) {
                // just leave it undefined
            }
        }

        final ModelNode store = result.get(PlatformMBeanConstants.OBJECT_NAME.getName());
        ThreadMXBeanAttributeHandler.storeResult(PlatformMBeanConstants.OBJECT_NAME.getName(), store);
    }
}
