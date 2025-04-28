/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;
import java.lang.management.PlatformLoggingMXBean;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.platform.mbean.logging.PlatformMBeanLogger;
import org.jboss.dmr.ModelNode;

/**
 * Handles read-attribute  for the resource representing {@link java.lang.management.PlatformLoggingMXBean}.
 */
class PlatformLoggingMXBeanAttributeHandler extends AbstractPlatformMBeanAttributeHandler {

    static final PlatformLoggingMXBeanAttributeHandler INSTANCE = new PlatformLoggingMXBeanAttributeHandler();

    private PlatformLoggingMXBeanAttributeHandler() {
    }

    @Override
    protected void executeWriteAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {
        // Shouldn't happen; the global handler should reject
        throw unknownAttribute(operation);

    }

    @Override
    protected void executeReadAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String name = operation.require(ModelDescriptionConstants.NAME).asString();

        try {
            if ((PlatformMBeanConstants.OBJECT_NAME.getName().equals(name))
                    || PlatformLoggingResourceDefinition.PLATFORM_LOGGING_READ_ATTRIBUTES.contains(name)) {
                storeResult(name, context.getResult());
            } else {
                // Shouldn't happen; the global handler should reject
                throw unknownAttribute(operation);
            }
        } catch (SecurityException | UnsupportedOperationException e) {
            throw new OperationFailedException(e.toString());
        }

    }

    static void storeResult(final String name, final ModelNode store) {

        if (PlatformMBeanConstants.OBJECT_NAME.getName().equals(name)) {
            store.set(PlatformMBeanConstants.PLATFORM_LOGGING_MXBEAN_NAME);
        } else if (PlatformMBeanConstants.LOGGER_NAMES.equals(name)) {
            store.setEmptyList();
            for (String loggerName : ManagementFactory.getPlatformMXBean(PlatformLoggingMXBean.class).getLoggerNames()) {
                store.add(loggerName);
            }
        } else if (PlatformLoggingResourceDefinition.PLATFORM_LOGGING_READ_ATTRIBUTES.contains(name)) {
            // Bug
            throw PlatformMBeanLogger.ROOT_LOGGER.badReadAttributeImpl(name);
        }
    }
}
