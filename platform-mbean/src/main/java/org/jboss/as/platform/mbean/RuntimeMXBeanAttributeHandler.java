/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.platform.mbean.logging.PlatformMBeanLogger;
import org.jboss.dmr.ModelNode;

/**
 * Handles read-attribute and write-attribute for the resource representing {@link java.lang.management.RuntimeMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class RuntimeMXBeanAttributeHandler extends AbstractPlatformMBeanAttributeHandler {

    static final RuntimeMXBeanAttributeHandler INSTANCE = new RuntimeMXBeanAttributeHandler();

    private RuntimeMXBeanAttributeHandler() {

    }

    @Override
    protected void executeReadAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String name = operation.require(ModelDescriptionConstants.NAME).asString();

        try {
            if ((PlatformMBeanConstants.OBJECT_NAME.getName().equals(name))
                    || RuntimeResourceDefinition.RUNTIME_READ_ATTRIBUTES.contains(name)
                    || RuntimeResourceDefinition.RUNTIME_METRICS.contains(name)) {
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

        // Shouldn't happen; the global handler should reject
        throw unknownAttribute(operation);

    }

    static void storeResult(final String name, final ModelNode store) {

        if (PlatformMBeanConstants.OBJECT_NAME.getName().equals(name)) {
            store.set(ManagementFactory.RUNTIME_MXBEAN_NAME);
        } else if (ModelDescriptionConstants.NAME.equals(name)) {
           String runtimeName;
           try {
              runtimeName = ManagementFactory.getRuntimeMXBean().getName();
           } catch (ArrayIndexOutOfBoundsException e) {
              // Workaround for OSX issue
              String localAddr;
              try {
                 localAddr = InetAddress.getByName(null).toString();
              } catch (UnknownHostException uhe) {
                 localAddr = "localhost";
              }
              runtimeName = new Random().nextInt() + "@" + localAddr;
           }
           store.set(runtimeName);
        } else if (PlatformMBeanConstants.VM_NAME.equals(name)) {
            store.set(ManagementFactory.getRuntimeMXBean().getVmName());
        } else if (PlatformMBeanConstants.VM_VENDOR.equals(name)) {
            store.set(ManagementFactory.getRuntimeMXBean().getVmVendor());
        } else if (PlatformMBeanConstants.VM_VERSION.equals(name)) {
            store.set(ManagementFactory.getRuntimeMXBean().getVmVersion());
        } else if (PlatformMBeanConstants.SPEC_NAME.equals(name)) {
            store.set(ManagementFactory.getRuntimeMXBean().getSpecName());
        } else if (PlatformMBeanConstants.SPEC_VENDOR.equals(name)) {
            store.set(ManagementFactory.getRuntimeMXBean().getSpecVendor());
        } else if (PlatformMBeanConstants.SPEC_VERSION.equals(name)) {
            store.set(ManagementFactory.getRuntimeMXBean().getSpecVersion());
        } else if (PlatformMBeanConstants.MANAGEMENT_SPEC_VERSION.equals(name)) {
            store.set(ManagementFactory.getRuntimeMXBean().getManagementSpecVersion());
        } else if (PlatformMBeanConstants.CLASS_PATH.equals(name)) {
            store.set(ManagementFactory.getRuntimeMXBean().getClassPath());
        } else if (PlatformMBeanConstants.LIBRARY_PATH.equals(name)) {
            store.set(ManagementFactory.getRuntimeMXBean().getLibraryPath());
        } else if (PlatformMBeanConstants.BOOT_CLASS_PATH_SUPPORTED.equals(name)) {
            store.set(ManagementFactory.getRuntimeMXBean().isBootClassPathSupported());
        } else if (PlatformMBeanConstants.BOOT_CLASS_PATH.equals(name)) {
            store.set(ManagementFactory.getRuntimeMXBean().getBootClassPath());
        } else if (PlatformMBeanConstants.INPUT_ARGUMENTS.equals(name)) {
            store.setEmptyList();
            for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                store.add(arg);
            }
        } else if (PlatformMBeanConstants.UPTIME.equals(name)) {
            store.set(ManagementFactory.getRuntimeMXBean().getUptime());
        } else if (PlatformMBeanConstants.START_TIME.equals(name)) {
            store.set(ManagementFactory.getRuntimeMXBean().getStartTime());
        } else if (PlatformMBeanConstants.SYSTEM_PROPERTIES.equals(name)) {
            store.setEmptyObject();
            final TreeMap<String, String> sorted = new TreeMap<>(ManagementFactory.getRuntimeMXBean().getSystemProperties());
            for (Map.Entry<String, String> prop : sorted.entrySet()) {
                final ModelNode propNode = store.get(prop.getKey());
                if (prop.getValue() != null) {
                    propNode.set(prop.getValue());
                }
            }
        } else if (RuntimeResourceDefinition.RUNTIME_READ_ATTRIBUTES.contains(name)
                || RuntimeResourceDefinition.RUNTIME_METRICS.contains(name)) {
            // Bug
            throw PlatformMBeanLogger.ROOT_LOGGER.badReadAttributeImpl(name);
        }

    }
}
