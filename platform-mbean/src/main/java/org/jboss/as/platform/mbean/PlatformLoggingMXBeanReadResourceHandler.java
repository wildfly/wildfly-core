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
 * Handles read-resource for the resource representing {@link java.lang.management.PlatformLoggingMXBean}.
 *
 */
public class PlatformLoggingMXBeanReadResourceHandler implements OperationStepHandler {

    public static final PlatformLoggingMXBeanReadResourceHandler INSTANCE = new PlatformLoggingMXBeanReadResourceHandler();

    private PlatformLoggingMXBeanReadResourceHandler() {
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final ModelNode result = context.getResult();

        for (String attribute : PlatformLoggingResourceDefinition.PLATFORM_LOGGING_READ_ATTRIBUTES) {
            final ModelNode store = result.get(attribute);
            try {
                PlatformLoggingMXBeanAttributeHandler.storeResult(attribute, store);
            } catch (SecurityException ignored) {
                // just leave it undefined
            } catch (UnsupportedOperationException ignored) {
                // just leave it undefined
            }
        }

        final ModelNode store = result.get(PlatformMBeanConstants.OBJECT_NAME.getName());
        PlatformLoggingMXBeanAttributeHandler.storeResult(PlatformMBeanConstants.OBJECT_NAME.getName(), store);
    }
}
