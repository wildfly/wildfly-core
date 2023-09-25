/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import static org.jboss.as.platform.mbean.ClassLoadingResourceDefinition.CLASSLOADING_METRICS;
import static org.jboss.as.platform.mbean.ClassLoadingResourceDefinition.CLASSLOADING_READ_WRITE_ATTRIBUTES;

import java.lang.management.ManagementFactory;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.platform.mbean.logging.PlatformMBeanLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Handles read-attribute and write-attribute for the resource representing {@link java.lang.management.ClassLoadingMXBean}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class ClassLoadingMXBeanAttributeHandler extends AbstractPlatformMBeanAttributeHandler {

    static final ClassLoadingMXBeanAttributeHandler INSTANCE = new ClassLoadingMXBeanAttributeHandler();

    private ClassLoadingMXBeanAttributeHandler() {
        writeAttributeValidator.registerValidator(ModelDescriptionConstants.VALUE, new ModelTypeValidator(ModelType.BOOLEAN));
    }

    @Override
    protected void executeReadAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final String name = operation.require(ModelDescriptionConstants.NAME).asString();

        if (PlatformMBeanConstants.OBJECT_NAME.getName().equals(name)) {
            context.getResult().set(ManagementFactory.CLASS_LOADING_MXBEAN_NAME);
        } else if (PlatformMBeanConstants.TOTAL_LOADED_CLASS_COUNT.equals(name)) {
            context.getResult().set(ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount());
        } else if (PlatformMBeanConstants.LOADED_CLASS_COUNT.equals(name)) {
            context.getResult().set(ManagementFactory.getClassLoadingMXBean().getLoadedClassCount());
        } else if (PlatformMBeanConstants.UNLOADED_CLASS_COUNT.equals(name)) {
            context.getResult().set(ManagementFactory.getClassLoadingMXBean().getUnloadedClassCount());
        } else if (PlatformMBeanConstants.VERBOSE.equals(name)) {
            context.getResult().set(ManagementFactory.getClassLoadingMXBean().isVerbose());
        } else if (CLASSLOADING_METRICS.contains(name)
                || CLASSLOADING_READ_WRITE_ATTRIBUTES.contains(name)) {
            // Bug
            throw PlatformMBeanLogger.ROOT_LOGGER.badReadAttributeImpl(name);
        } else {
            // Shouldn't happen; the global handler should reject
            throw unknownAttribute(operation);
        }

    }

    @Override
    protected void executeWriteAttribute(OperationContext context, ModelNode operation) throws OperationFailedException {
        final String name = operation.require(ModelDescriptionConstants.NAME).asString();
        if (PlatformMBeanConstants.VERBOSE.equals(name)) {
            context.getServiceRegistry(true); //to trigger auth
            ManagementFactory.getClassLoadingMXBean().setVerbose(operation.require(ModelDescriptionConstants.VALUE).asBoolean());
        } else if (CLASSLOADING_READ_WRITE_ATTRIBUTES.contains(name)) {
            // Bug
            throw PlatformMBeanLogger.ROOT_LOGGER.badWriteAttributeImpl(name);
        } else {
            // Shouldn't happen; the global handler should reject
            throw unknownAttribute(operation);
        }

    }
}
