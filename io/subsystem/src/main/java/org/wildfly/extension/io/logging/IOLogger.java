/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io.logging;

import static org.jboss.logging.Logger.Level.INFO;
import static org.jboss.logging.Logger.Level.WARN;

import java.net.InetSocketAddress;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.wildfly.common.net.CidrAddress;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
@MessageLogger(projectCode = "WFLYIO", length = 3)
public interface IOLogger extends BasicLogger {

    /**
     * A root logger with the category of the package name.
     */
    IOLogger ROOT_LOGGER = Logger.getMessageLogger(IOLogger.class, "org.wildfly.extension.io");


    @LogMessage(level = INFO)
    @Message(id = 1, value = "Worker '%s' has auto-configured to %d IO threads with %d max task threads based on your %d available processors")
    void printDefaults(String workerName, int ioThreads, int workerThreads, int cpuCount);

    @LogMessage(level = INFO)
    @Message(id = 2, value = "Worker '%s' has auto-configured to %d IO threads based on your %d available processors")
    void printDefaultsIoThreads(String workerName, int ioThreads, int cpuCount);

    @LogMessage(level = INFO)
    @Message(id = 3, value = "Worker '%s' has auto-configured to %d max task threads based on your %d available processors")
    void printDefaultsWorkerThreads(String workerName, int workerThreads, int cpuCount);

    @LogMessage(level = WARN)
    @Message(id = 4, value = "Worker '%s' would auto-configure to %d max task threads based on %d available processors, however your system does not have enough file descriptors configured to support this configuration. It is likely you will experience application degradation unless you increase your file descriptor limit.")
    void lowFD(String workerName, int suggestedWorkerThreadCount, int cpuCount);

    @LogMessage(level = WARN)
    @Message(id = 5, value = "Your system is configured with %d file descriptors, but your current application server configuration will require a minimum of %d (and probably more than that); attempting to adjust, however you should expect stability problems unless you increase this number")
    void lowGlobalFD(int maxFd, int requiredCount);

    @Message(id = 6, value = "no metrics available")
    String noMetrics();

    @Message(id = 7, value = "Unexpected bind address conflict in resource \"%s\" when attempting to establish binding for destination %s to %s: a binding of %s already existed")
    OperationFailedException unexpectedBindAddressConflict(PathAddress currentAddress, CidrAddress cidrAddress, InetSocketAddress bindAddress, InetSocketAddress existing);

    @LogMessage(level = WARN)
    @Message(id = 8, value = "The stack-size value of %d bytes for IO worker %s is low and may result in problems. A value of at least 150,000 is recommended.")
    void wrongStackSize(long val, String workerName);
}
