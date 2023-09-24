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
import org.jboss.as.controller.operations.validation.ParametersValidator;
import org.jboss.as.platform.mbean.logging.PlatformMBeanLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Handles read-attribute and write-attribute for the resource representing {@link java.lang.management.ThreadMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class ThreadMXBeanAttributeHandler extends AbstractPlatformMBeanAttributeHandler {

    static final ThreadMXBeanAttributeHandler INSTANCE = new ThreadMXBeanAttributeHandler();

    private final ParametersValidator enabledValidator = new ParametersValidator();

    private ThreadMXBeanAttributeHandler() {
        enabledValidator.registerValidator(ModelDescriptionConstants.VALUE, new ModelTypeValidator(ModelType.BOOLEAN));
    }

    @Override
    protected void executeReadAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String name = operation.require(ModelDescriptionConstants.NAME).asString();

        try {
            if ((PlatformMBeanConstants.OBJECT_NAME.getName().equals(name))
                    || ThreadResourceDefinition.THREADING_READ_ATTRIBUTES.contains(name)
                    || ThreadResourceDefinition.THREADING_READ_WRITE_ATTRIBUTES.contains(name)
                    || ThreadResourceDefinition.THREADING_METRICS.contains(name)) {
                storeResult(name, context.getResult());
            } else {
                // Shouldn't happen; the global handler should reject
                throw unknownAttribute(operation);
            }
        } catch (SecurityException | UnsupportedOperationException e) {
            throw new OperationFailedException(e.toString());
        }

    }

    @Override
    protected void executeWriteAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String name = operation.require(ModelDescriptionConstants.NAME).asString();

        try {
            if (PlatformMBeanConstants.THREAD_CONTENTION_MONITORING_ENABLED.equals(name)) {
                enabledValidator.validate(operation);
                context.getServiceRegistry(true); //to trigger auth
                ManagementFactory.getThreadMXBean().setThreadContentionMonitoringEnabled(operation.require(ModelDescriptionConstants.VALUE).asBoolean());
            } else if (PlatformMBeanConstants.THREAD_CPU_TIME_ENABLED.equals(name)) {
                enabledValidator.validate(operation);
                context.getServiceRegistry(true); //to trigger auth
                ManagementFactory.getThreadMXBean().setThreadCpuTimeEnabled(operation.require(ModelDescriptionConstants.VALUE).asBoolean());
            } else if (ThreadResourceDefinition.THREADING_READ_WRITE_ATTRIBUTES.contains(name)) {
                // Bug
                throw PlatformMBeanLogger.ROOT_LOGGER.badWriteAttributeImpl(name);
            } else {
                // Shouldn't happen; the global handler should reject
                throw unknownAttribute(operation);
            }
        } catch (SecurityException e) {
            throw new OperationFailedException(e.toString());
        } catch (UnsupportedOperationException e) {
            throw new OperationFailedException(e.toString());
        }

    }

    static void storeResult(final String name, final ModelNode store) {

        if (PlatformMBeanConstants.OBJECT_NAME.getName().equals(name)) {
            store.set(ManagementFactory.THREAD_MXBEAN_NAME);
        } else if (PlatformMBeanConstants.THREAD_COUNT.equals(name)) {
            store.set(ManagementFactory.getThreadMXBean().getThreadCount());
        } else if (PlatformMBeanConstants.PEAK_THREAD_COUNT.equals(name)) {
            store.set(ManagementFactory.getThreadMXBean().getPeakThreadCount());
        } else if (PlatformMBeanConstants.TOTAL_STARTED_THREAD_COUNT.equals(name)) {
            store.set(ManagementFactory.getThreadMXBean().getTotalStartedThreadCount());
        } else if (PlatformMBeanConstants.DAEMON_THREAD_COUNT.equals(name)) {
            store.set(ManagementFactory.getThreadMXBean().getDaemonThreadCount());
        } else if (PlatformMBeanConstants.ALL_THREAD_IDS.equals(name)) {
            store.setEmptyList();
            for (Long id : ManagementFactory.getThreadMXBean().getAllThreadIds()) {
                store.add(id);
            }
        } else if (PlatformMBeanConstants.THREAD_CONTENTION_MONITORING_SUPPORTED.equals(name)) {
            store.set(ManagementFactory.getThreadMXBean().isThreadContentionMonitoringSupported());
        } else if (PlatformMBeanConstants.THREAD_CONTENTION_MONITORING_ENABLED.equals(name)) {
            store.set(ManagementFactory.getThreadMXBean().isThreadContentionMonitoringEnabled());
        } else if (PlatformMBeanConstants.CURRENT_THREAD_CPU_TIME.equals(name)) {
            store.set(ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime());
        } else if (PlatformMBeanConstants.CURRENT_THREAD_USER_TIME.equals(name)) {
            store.set(ManagementFactory.getThreadMXBean().getCurrentThreadUserTime());
        } else if (PlatformMBeanConstants.THREAD_CPU_TIME_SUPPORTED.equals(name)) {
            store.set(ManagementFactory.getThreadMXBean().isThreadCpuTimeSupported());
        } else if (PlatformMBeanConstants.CURRENT_THREAD_CPU_TIME_SUPPORTED.equals(name)) {
            store.set(ManagementFactory.getThreadMXBean().isCurrentThreadCpuTimeSupported());
        } else if (PlatformMBeanConstants.THREAD_CPU_TIME_ENABLED.equals(name)) {
            store.set(ManagementFactory.getThreadMXBean().isThreadCpuTimeEnabled());
        } else if (PlatformMBeanConstants.OBJECT_MONITOR_USAGE_SUPPORTED.equals(name)) {
            store.set(ManagementFactory.getThreadMXBean().isObjectMonitorUsageSupported());
        } else if (PlatformMBeanConstants.SYNCHRONIZER_USAGE_SUPPORTED.equals(name)) {
            store.set(ManagementFactory.getThreadMXBean().isSynchronizerUsageSupported());
        } else if (ThreadResourceDefinition.THREADING_READ_ATTRIBUTES.contains(name)
                || ThreadResourceDefinition.THREADING_READ_WRITE_ATTRIBUTES.contains(name)
                || ThreadResourceDefinition.THREADING_METRICS.contains(name)) {
            // Bug
            throw PlatformMBeanLogger.ROOT_LOGGER.badReadAttributeImpl(name);
        }

    }
}
