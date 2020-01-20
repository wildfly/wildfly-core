/*
Copyright 2020 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.cli.impl._private;

import java.io.File;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.Logger;
import static org.jboss.logging.Logger.Level.DEBUG;
import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.INFO;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;

/**
 *
 * @author jdenise
 */
@MessageLogger(projectCode = "WFLYCLI", length = 4)
public interface BootScriptInvokerLogger extends BasicLogger {

    /**
     * Default root logger with category of the package name.
     */
    BootScriptInvokerLogger ROOT_LOGGER = Logger.getMessageLogger(BootScriptInvokerLogger.class, "org.jboss.as.cli");

    /**
     * Logs an info message to advertise that a script file is being processed.
     *
     * @param file the CLI script file.
     */
    @LogMessage(level = INFO)
    @Message(id = 1, value = "Processing CLI script %s")
    void processScript(File file);

    /**
     * Logs an error message to advertise that CLI script processing failed.
     *
     * @param file the CLI script file.
     */
    @LogMessage(level = ERROR)
    @Message(id = 2, value = "Error processing CLI script %s")
    void errorProcessingScript(File file);

    /**
     * Logs an error message to advertise that CLI script file was not found.
     *
     * @param file the CLI script file.
     * @return Exception to throw.
     */
    @Message(id = 3, value = "Could not find CLI properties file %s")
    RuntimeException propertiesFileNotFound(File file);

    /**
     * Logs an error message to advertise that CLI script output is printed.
     *
     */
    @LogMessage(level = ERROR)
    @Message(id = 4, value = "CLI execution output:")
    void cliOutput();

    /**
     * Logs an info message to advertise that the script file processing is
     * done.
     *
     * @param file the CLI script file.
     */
    @LogMessage(level = INFO)
    @Message(id = 5, value = "Done processing CLI script %s")
    void doneProcessScript(File file);

    /**
     * Logs a debug message to advertise the command being executed.
     *
     * @param cmd the CLI command.
     */
    @LogMessage(level = DEBUG)
    @Message(id = 6, value = "Executing CLI command %s")
    void executeCommand(String cmd);

    /**
     * Logs an error message to advertise that an unexpected exception was
     * thrown.
     *
     * @param cause
     * @param file CLI script file
     * @return Exception to throw
     */
    @Message(id = 7, value = "Unexpected exception while processing CLI commands from %s")
    IllegalStateException unexpectedException(@Cause Throwable cause, File file);

    /**
     * Logs an error message to advertise that an error occurred.
     *
     * @param script CLI script
     * @param errors File that contains error messages
     */
    @LogMessage(level = ERROR)
    @Message(id = 8, value = "Error processing CLI script %s. The Operations were executed but "
            + "there were unexpected values. See list of errors in %s")
    void unexpectedErrors(File script, File errors);
}
