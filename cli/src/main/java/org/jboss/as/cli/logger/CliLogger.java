/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.cli.logger;

import org.jboss.dmr.ModelNode;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

import java.util.Collection;

@MessageLogger(projectCode = "WFLYCLI")
public interface CliLogger extends BasicLogger {

    CliLogger ROOT_LOGGER = Logger.getMessageLogger(CliLogger.class, CliLogger.class.getPackage().getName());

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 1000, value = "Error processing CLI")
    void consoleError(@Cause Throwable cause);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 1025, value = "access-control is missing defaults: %s")
    void accessControlMissingDefaults(ModelNode accessControl);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 1026, value = "access-control/default is missing operations: %s")
    void accessControlMissingOperations(ModelNode accessControl);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 1028, value = "'execute' is missing for %s in %s")
    void executeMissingForOperation(String operation, ModelNode accessControl);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 1030, value = "Failed to execute %s")
    void failedToExecute(String name, @Cause Exception cause);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 1032, value = "Response is missing result for %s: %s")
    void responseMissingResult(String name, ModelNode response);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 1033, value = "Result is missing access-control for %s: %s")
    void resultMissingAccessControl(String name, ModelNode response);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 1036, value = "Exception during reconnecting")
    void reconnectingException(@Cause Exception cause);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 1037, value = "Invalid URI")
    void invalidURI(@Cause Exception cause);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 1039, value = "JConsoleCLIPlugin error")
    void jConsoleCLIPluginError(@Cause Exception cause);

    @LogMessage(level = Logger.Level.ERROR)
    @Message(id = 1044, value = "Print message error: %s")
    void printMessageError(String message);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 1045, value = "Print message: %s")
    void printMessageInfo(String message);

    @LogMessage(level = Logger.Level.INFO)
    @Message(id = 1046, value = "Print columns: %s")
    void printColumns(Collection<String> columns);

    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 1050, value = "Error parsing line: %s")
    void errorParsingLine(String message, @Cause Exception cause);
}
