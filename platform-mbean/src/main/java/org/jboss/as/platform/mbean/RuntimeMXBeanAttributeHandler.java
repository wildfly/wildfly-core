/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
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
                context.getResult().set(getResult(name, ManagementFactory.getRuntimeMXBean()));
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

    static ModelNode getResult(final String name, final RuntimeMXBean mbean) {

        ModelNode store;
        if (PlatformMBeanConstants.OBJECT_NAME.getName().equals(name)) {
            store = new ModelNode(ManagementFactory.RUNTIME_MXBEAN_NAME);
        } else if (ModelDescriptionConstants.NAME.equals(name)) {
           String runtimeName;
           try {
              runtimeName = mbean.getName();
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
            store = new ModelNode(runtimeName);
        } else if (PlatformMBeanConstants.VM_NAME.equals(name)) {
            store = new ModelNode(mbean.getVmName());
        } else if (PlatformMBeanConstants.VM_VENDOR.equals(name)) {
            store = new ModelNode(mbean.getVmVendor());
        } else if (PlatformMBeanConstants.VM_VERSION.equals(name)) {
            store = new ModelNode(mbean.getVmVersion());
        } else if (PlatformMBeanConstants.SPEC_NAME.equals(name)) {
            store = new ModelNode(mbean.getSpecName());
        } else if (PlatformMBeanConstants.SPEC_VENDOR.equals(name)) {
            store = new ModelNode(mbean.getSpecVendor());
        } else if (PlatformMBeanConstants.SPEC_VERSION.equals(name)) {
            store = new ModelNode(mbean.getSpecVersion());
        } else if (PlatformMBeanConstants.MANAGEMENT_SPEC_VERSION.equals(name)) {
            store = new ModelNode(mbean.getManagementSpecVersion());
        } else if (PlatformMBeanConstants.CLASS_PATH.equals(name)) {
            store = new ModelNode(mbean.getClassPath());
        } else if (PlatformMBeanConstants.LIBRARY_PATH.equals(name)) {
            store = new ModelNode(mbean.getLibraryPath());
        } else if (PlatformMBeanConstants.BOOT_CLASS_PATH_SUPPORTED.equals(name)) {
            store = new ModelNode(mbean.isBootClassPathSupported());
        } else if (PlatformMBeanConstants.BOOT_CLASS_PATH.equals(name)) {
            store = new ModelNode(mbean.getBootClassPath());
        } else if (PlatformMBeanConstants.INPUT_ARGUMENTS.equals(name)) {
            store = new ModelNode();
            store.setEmptyList();
            for (String arg : mbean.getInputArguments()) {
                store.add(arg);
            }
        } else if (PlatformMBeanConstants.UPTIME.equals(name)) {
            store = new ModelNode(mbean.getUptime());
        } else if (PlatformMBeanConstants.START_TIME.equals(name)) {
            store = new ModelNode(mbean.getStartTime());
        } else if (PlatformMBeanConstants.SYSTEM_PROPERTIES.equals(name)) {
            store = new ModelNode();
            store.setEmptyObject();
            final TreeMap<String, String> sorted = new TreeMap<>(ManagementFactory.getRuntimeMXBean().getSystemProperties());
            for (Map.Entry<String, String> prop : sorted.entrySet()) {
                final ModelNode propNode = store.get(prop.getKey());
                if (prop.getValue() != null) {
                    propNode.set(prop.getValue());
                }
            }
        } else if (PlatformMBeanConstants.PID.equals(name)) {
            store = new ModelNode(mbean.getPid());
        } else if (RuntimeResourceDefinition.RUNTIME_READ_ATTRIBUTES.contains(name)
                || RuntimeResourceDefinition.RUNTIME_METRICS.contains(name)) {
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
}
