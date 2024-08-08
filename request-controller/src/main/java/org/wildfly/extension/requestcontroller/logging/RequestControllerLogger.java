/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.requestcontroller.logging;

import static org.jboss.logging.Logger.Level.WARN;

import java.lang.invoke.MethodHandles;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * @author Stuart Douglas
 */
@MessageLogger(projectCode = "WFLYREQCON", length = 3)
public interface RequestControllerLogger extends BasicLogger {

    /**
     * A root logger with the category of the package name.
     */
    RequestControllerLogger ROOT_LOGGER = Logger.getMessageLogger(MethodHandles.lookup(), RequestControllerLogger.class, "org.wildfly.extension.requestcontroller");


    @LogMessage(level = WARN)
    @Message(id = 1, value = "Failed to cancel queued task %s")
    void failedToCancelTask(Object task, @Cause Exception e);

}
