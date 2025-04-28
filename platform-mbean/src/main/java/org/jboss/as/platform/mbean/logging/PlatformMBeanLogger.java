/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean.logging;

import java.lang.invoke.MethodHandles;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
@MessageLogger(projectCode = "WFLYPMB", length = 4)
public interface PlatformMBeanLogger extends BasicLogger {

    /**
     * A logger with the category of the package.
     */
    PlatformMBeanLogger ROOT_LOGGER = Logger.getMessageLogger(MethodHandles.lookup(), PlatformMBeanLogger.class, "org.jboss.as.platform.mbean");

    /**
     * Creates an exception indicating that an operation parameter attribute name is unknown
     *
     * @param attributeName the name of the attribute
     *
     * @return the {@link OperationFailedException}
     */
    @Message(id = 1, value = "No known attribute %s")
    OperationFailedException unknownAttribute(String attributeName);

    @Message(id = 2, value = "A platform mbean resource does not have a writable model")
    UnsupportedOperationException modelNotWritable();

    @Message(id = 3, value = "Adding child resources is not supported")
    UnsupportedOperationException addingChildrenNotSupported();

    @Message(id = 4, value = "Removing child resources is not supported")
    UnsupportedOperationException removingChildrenNotSupported();

    @Message(id = 5, value = "No BufferPoolMXBean with name '%s' currently exists")
    OperationFailedException unknownBufferPool(String poolName);

    @Message(id = 6, value = "Read support for attribute %s was not properly implemented")
    IllegalStateException badReadAttributeImpl(String attributeName);

    @Message(id = 7, value = "Write support for attribute %s was not properly implemented")
    IllegalStateException badWriteAttributeImpl(String attributeName);

    @Message(id = 8, value = "No GarbageCollectorMXBean with name %s currently exists")
    OperationFailedException unknownGarbageCollector(String gcName);

    @Message(id = 9, value = "No MemoryManagerMXBean with name %s currently exists")
    OperationFailedException unknownMemoryManager(String mmName);

    @Message(id = 10, value = "No MemoryPoolMXBean with name %s currently exists")
    OperationFailedException unknownMemoryPool(String mmName);

    @Message(id = 11, value = "Operation %s is not supported by the underlying JVM")
    OperationFailedException unsupportedOperation(String operation);

    @Message(id = 12, value = "Attribute %s is not supported by the underlying JVM and can't be written.")
    OperationFailedException unsupportedWritableAttribute(String attribute);

}
