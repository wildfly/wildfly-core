/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

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
                context.getResult().set(getResult(name, ManagementFactory.getThreadMXBean()));
            } else {
                if ( ThreadResourceDefinition.THREADING_EXTENDED_READ_ATTRIBUTES.contains(name)
                    || ThreadResourceDefinition.THREADING_EXTENDED_READ_WRITE_ATTRIBUTES.contains(name)
                    || ThreadResourceDefinition.THREADING_EXTENDED_METRICS.contains(name)) {
                    context.getResult().set(getExtendedResult(name, null));
                } else {
                    // Shouldn't happen; the global handler should reject
                    throw unknownAttribute(operation);
                }
            }
        } catch (SecurityException | UnsupportedOperationException e) {
            throw new OperationFailedException(e.toString());
        }

    }

    static ModelNode getExtendedResult(String name, ThreadMXBean ignored) {
        ExtendedThreadMBean mbean = new ExtendedThreadMBean();
        ModelNode store;
        if (PlatformMBeanConstants.CURRENT_THREAD_ALLOCATED_BYTES.equals(name)) {
            if (mbean.isAttributeDefined(ExtendedThreadMBean.CURRENT_THREAD_ALLOCATED_BYTES_ATTRIBUTE)) {
                store = new ModelNode(mbean.getCurrentThreadAllocatedBytes());
            } else {
                store = new ModelNode();
            }
        } else if (PlatformMBeanConstants.THREAD_ALLOCATED_MEMORY_ENABLED.equals(name)) {
            if (mbean.isAttributeDefined(ExtendedThreadMBean.THREAD_ALLOCATED_MEMORY_ENABLED_ATTRIBUTE)) {
                store = new ModelNode(mbean.isThreadAllocatedMemoryEnabled());
            } else {
                store = new ModelNode();
            }
        } else if (PlatformMBeanConstants.THREAD_ALLOCATED_MEMORY_SUPPORTED.equals(name)) {
            if (mbean.isAttributeDefined(ExtendedThreadMBean.THREAD_ALLOCATED_MEMORY_SUPPORTED_ATTRIBUTE)) {
                store = new ModelNode(mbean.isThreadAllocatedMemorySupported());
            } else {
                store = new ModelNode();
            }
        } else {
            // TODO should not happen and we should fail, but historically this would have resulted
            //  in an undefined node, so we keep it that way
            // throw new IllegalArgumentException(name);
            store = new ModelNode();
        }
        return store;
    }

    @Override
    protected void executeWriteAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String name = operation.require(ModelDescriptionConstants.NAME).asString();
        try {
            if (ThreadResourceDefinition.THREADING_EXTENDED_READ_WRITE_ATTRIBUTES.contains(name)) {
               executeExtendedWriteAttribute(context, operation, name);
            } else {
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
            }
        } catch (SecurityException e) {
            throw new OperationFailedException(e.toString());
        } catch (UnsupportedOperationException e) {
            throw new OperationFailedException(e.toString());
        }

    }

    static ModelNode getResult(final String name, final ThreadMXBean mbean) {

        ModelNode store;
        if (PlatformMBeanConstants.OBJECT_NAME.getName().equals(name)) {
            store = new ModelNode(ManagementFactory.THREAD_MXBEAN_NAME);
        } else if (PlatformMBeanConstants.THREAD_COUNT.equals(name)) {
            store = new ModelNode(mbean.getThreadCount());
        } else if (PlatformMBeanConstants.PEAK_THREAD_COUNT.equals(name)) {
            store = new ModelNode(mbean.getPeakThreadCount());
        } else if (PlatformMBeanConstants.TOTAL_STARTED_THREAD_COUNT.equals(name)) {
            store = new ModelNode(mbean.getTotalStartedThreadCount());
        } else if (PlatformMBeanConstants.DAEMON_THREAD_COUNT.equals(name)) {
            store = new ModelNode(mbean.getDaemonThreadCount());
        } else if (PlatformMBeanConstants.ALL_THREAD_IDS.equals(name)) {
            store = new ModelNode();
            store.setEmptyList();
            for (Long id : mbean.getAllThreadIds()) {
                store.add(id);
            }
        } else if (PlatformMBeanConstants.THREAD_CONTENTION_MONITORING_SUPPORTED.equals(name)) {
            store = new ModelNode(mbean.isThreadContentionMonitoringSupported());
        } else if (PlatformMBeanConstants.THREAD_CONTENTION_MONITORING_ENABLED.equals(name)) {
            store = new ModelNode(mbean.isThreadContentionMonitoringEnabled());
        } else if (PlatformMBeanConstants.CURRENT_THREAD_CPU_TIME.equals(name)) {
            store = new ModelNode(mbean.getCurrentThreadCpuTime());
        } else if (PlatformMBeanConstants.CURRENT_THREAD_USER_TIME.equals(name)) {
            store = new ModelNode(mbean.getCurrentThreadUserTime());
        } else if (PlatformMBeanConstants.THREAD_CPU_TIME_SUPPORTED.equals(name)) {
            store = new ModelNode(mbean.isThreadCpuTimeSupported());
        } else if (PlatformMBeanConstants.CURRENT_THREAD_CPU_TIME_SUPPORTED.equals(name)) {
            store = new ModelNode(mbean.isCurrentThreadCpuTimeSupported());
        } else if (PlatformMBeanConstants.THREAD_CPU_TIME_ENABLED.equals(name)) {
            store = new ModelNode(mbean.isThreadCpuTimeEnabled());
        } else if (PlatformMBeanConstants.OBJECT_MONITOR_USAGE_SUPPORTED.equals(name)) {
            store = new ModelNode(mbean.isObjectMonitorUsageSupported());
        } else if (PlatformMBeanConstants.SYNCHRONIZER_USAGE_SUPPORTED.equals(name)) {
            store = new ModelNode(mbean.isSynchronizerUsageSupported());
        } else if (ThreadResourceDefinition.THREADING_READ_ATTRIBUTES.contains(name)
                || ThreadResourceDefinition.THREADING_READ_WRITE_ATTRIBUTES.contains(name)
                || ThreadResourceDefinition.THREADING_METRICS.contains(name)) {
            // Bug
            throw PlatformMBeanLogger.ROOT_LOGGER.badReadAttributeImpl(name);
        } else {
            // TODO should not happen and we should fail, but historically this would have resulted
            //  in an undefined node, so we keep it that way
            // throw new IllegalArgumentException(name);
            store = new ModelNode();
        }
        return store;

    }

    private void executeExtendedWriteAttribute(OperationContext context, ModelNode operation, String name) throws OperationFailedException {
        ExtendedThreadMBean mbean = new ExtendedThreadMBean();
        if (PlatformMBeanConstants.THREAD_ALLOCATED_MEMORY_ENABLED.equals(name)) {
            if (mbean.isAttributeDefined(ExtendedThreadMBean.THREAD_ALLOCATED_MEMORY_ENABLED_ATTRIBUTE)) {
                enabledValidator.validate(operation);
                context.getServiceRegistry(true); //to trigger auth
                mbean.setThreadAllocatedMemoryEnabled(operation.require(ModelDescriptionConstants.VALUE).asBoolean());
            } else {
                throw PlatformMBeanLogger.ROOT_LOGGER.unsupportedWritableAttribute(PlatformMBeanConstants.THREAD_ALLOCATED_MEMORY_ENABLED);
            }
        } else if (ThreadResourceDefinition.THREADING_EXTENDED_READ_WRITE_ATTRIBUTES.contains(name)) {
            // Bug
            throw PlatformMBeanLogger.ROOT_LOGGER.badWriteAttributeImpl(name);
        }
    }
}
