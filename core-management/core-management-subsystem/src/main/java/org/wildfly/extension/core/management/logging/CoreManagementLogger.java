/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management.logging;

import static org.jboss.logging.Logger.Level.ERROR;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 *
 * @author Emmanuel Hugonnet (c) 2016 Red Hat, inc.
 */
@MessageLogger(projectCode = "WFLYCM", length = 4)
public interface CoreManagementLogger extends BasicLogger {

    /**
     * A logger with a category of the package name.
     */
    CoreManagementLogger ROOT_LOGGER = Logger.getMessageLogger(CoreManagementLogger.class, "org.wildfly.extension.core.management");

//    @Message(id = 1, value = "The resource %s wasn't working properly and has been removed.")
//    String removedOutOfOrderResource(final String address);

    @Message(id = 2, value = "Error initializing the process state listener %s")
    String processStateInitError(@Cause Throwable t, final String name);

    @LogMessage(level = ERROR)
    @Message(id = 3, value = "Error invoking the process state listener %s")
    void processStateInvokationError(@Cause Throwable t, final String name);

    @LogMessage(level = ERROR)
    @Message(id = 4, value = "The process state listener %s took to much time to complete.")
    void processStateTimeoutError(@Cause Throwable t, final String name);

    @LogMessage(level = ERROR)
    @Message(id = 5, value = "Error cleaning up for the process state listener %s")
    void processStateCleanupError(@Cause Throwable t, final String name);

    @Message(id = 6, value = "Error to load module %s")
    OperationFailedException errorToLoadModule(String moduleID);

    @Message(id = 7, value = "Error to load class %s from module %s")
    OperationFailedException errorToLoadModuleClass(String className, String moduleID);

    @Message(id = 8, value = "Error to instantiate instance of class %s from module %s")
    OperationFailedException errorToInstantiateClassInstanceFromModule(String className, String moduleID);
}
