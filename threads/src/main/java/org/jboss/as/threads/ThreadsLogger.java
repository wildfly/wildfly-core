/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.Param;
import org.jboss.msc.service.ServiceName;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MessageLogger(projectCode = "WFLYTHR", length = 4)
interface ThreadsLogger extends BasicLogger {

    /**
     * A logger with a category of the package name.
     */
    ThreadsLogger ROOT_LOGGER = Logger.getMessageLogger(ThreadsLogger.class, "org.jboss.as.threads");

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 1, value = "The '%s' attribute is no longer supported. The value [%f] of the '%s' attribute " +
                    "is being combined with the value [%f] of the '%s' attribute and the current processor count [%d] " +
                    "to derive a new value of [%d] for '%s'.")
    void perCpuNotSupported(Attribute perCpuAttr, BigDecimal count, Attribute countAttr, BigDecimal perCpu, Attribute perCpuAgain,
                            int processors, int fullCount, Attribute countAttrAgain);


    @Message(id = 2, value = "Unsupported attribute '%s'")
    IllegalStateException unsupportedBoundedQueueThreadPoolMetric(String attributeName);

    @Message(id = 3, value = "Unsupported attribute '%s'")
    IllegalStateException unsupportedBoundedQueueThreadPoolAttribute(String attributeName);

    @Message(id = 4, value = "Unsupported attribute '%s'")
    IllegalStateException unsupportedQueuelessThreadPoolMetric(String attributeName);

    @Message(id = 5, value = "Unsupported attribute '%s'")
    IllegalStateException unsupportedQueuelessThreadPoolAttribute(String attributeName);

    @Message(id = 6, value = "Unsupported attribute '%s'")
    IllegalStateException unsupportedScheduledThreadPoolMetric(String attributeName);

    @Message(id = 7, value = "Unsupported attribute '%s'")
    IllegalStateException unsupportedScheduledThreadPoolAttribute(String attributeName);

    @Message(id = 8, value = "Unsupported attribute '%s'")
    IllegalStateException unsupportedThreadFactoryAttribute(String attributeName);

    @Message(id = 9, value = "Unsupported attribute '%s'")
    IllegalStateException unsupportedUnboundedQueueThreadPoolMetric(String attributeName);

    @Message(id = 10, value = "Unsupported attribute '%s'")
    IllegalStateException unsupportedUnboundedQueueThreadPoolAttribute(String attributeName);

    @Message(id = 11, value = "The executor service hasn't been initialized.")
    IllegalStateException boundedQueueThreadPoolExecutorUninitialized();

    @Message(id = 12, value = "The executor service hasn't been initialized.")
    IllegalStateException queuelessThreadPoolExecutorUninitialized();

    @Message(id = 13, value = "The executor service hasn't been initialized.")
    IllegalStateException scheduledThreadPoolExecutorUninitialized();

    @Message(id = 14, value = "The thread factory service hasn't been initialized.")
    IllegalStateException threadFactoryUninitialized();

    @Message(id = 15, value = "The executor service hasn't been initialized.")
    IllegalStateException unboundedQueueThreadPoolExecutorUninitialized();

    @Message(id = 16, value = "Service '%s' not found.")
    OperationFailedException boundedQueueThreadPoolServiceNotFound(ServiceName serviceName);

    @Message(id = 17, value = "Service '%s' not found.")
    OperationFailedException queuelessThreadPoolServiceNotFound(ServiceName serviceName);

    @Message(id = 18, value = "Service '%s' not found.")
    OperationFailedException scheduledThreadPoolServiceNotFound(ServiceName serviceName);

    @Message(id = 19, value = "Service '%s' not found.")
    OperationFailedException threadFactoryServiceNotFound(ServiceName serviceName);

    @Message(id = 20, value = "Service '%s' not found.")
    OperationFailedException unboundedQueueThreadPoolServiceNotFound(ServiceName serviceName);

    @Message(id = 21, value = "Failed to locate executor service '%s'")
    OperationFailedException threadPoolServiceNotFoundForMetrics(ServiceName serviceName);

//    @Message(id = 22, value = "Attribute %s expects values of type %s but got %s of type %s")
//    OperationFailedException invalidKeepAliveType(String parameterName, ModelType objectType, ModelNode value, ModelType valueType);

//    @Message(id = 23, value = "Attribute %s expects values consisting of '%s' and '%s' but the new value consists of %s")
//    OperationFailedException invalidKeepAliveKeys(String parameterName, String time, String unit, Set<String> keys);

    @Message(id = 24, value = "Missing '%s' for parameter '%s'")
    OperationFailedException missingKeepAliveTime(String time, String parameterName);

    @Message(id = 25, value = "Missing '%s' for parameter '%s'")
    OperationFailedException missingKeepAliveUnit(String unit, String parameterName);

    // id = 26; redundant parameter null check message

    @Message(id = 27, value = "%s must be greater than or equal to zero")
    XMLStreamException countMustBePositive(Attribute count, @Param Location location);

    @Message(id = 28, value = "%s must be greater than or equal to zero")
    XMLStreamException perCpuMustBePositive(Attribute perCpu, @Param Location location);

    @Message(id = 29, value = "Missing '%s' for '%s'")
    OperationFailedException missingTimeSpecTime(String time, String parameterName);

    @Message(id = 30, value = "Failed to parse '%s', allowed values are: %s")
    OperationFailedException failedToParseUnit(String unit, List<TimeUnit> allowed);

    @Message(id = 31, value = "Unsupported attribute '%s'")
    IllegalStateException unsupportedEnhancedQueueExecutorAttribute(String attributeName);

    @Message(id = 32, value = "Service '%s' not found.")
    OperationFailedException enhancedQueueExecutorServiceNotFound(ServiceName serviceName);

    @Message(id = 33, value = "The executor service hasn't been initialized.")
    IllegalStateException enhancedQueueExecutorUninitialized();

    @Message(id = 34, value = "Unsupported metric '%s'")
    IllegalStateException unsupportedEnhancedQueueExecutorMetric(String attributeName);

    // id = 35; redundant parameter null check message
}
