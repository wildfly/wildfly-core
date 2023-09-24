/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.embedded.logging;

import java.io.IOException;
import java.lang.reflect.Method;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.modules.ModuleLoader;
import org.wildfly.core.embedded.Context;
import org.wildfly.core.embedded.EmbeddedProcessStartException;

/**
 * Date: 05.11.2011
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@MessageLogger(projectCode = "WFLYEMB", length = 4)
public interface EmbeddedLogger extends BasicLogger {

    /**
     * A logger with a category of the package name.
     */
    EmbeddedLogger ROOT_LOGGER = Logger.getMessageLogger(EmbeddedLogger.class, "org.jboss.as.embedded");

//    /**
//     * Logs a warning message indicating the file handle, represented by the {@code file} parameter, could not be
//     * closed.
//     *
//     * @param cause the cause of the error.
//     * @param file  the file.
//     */
//    @LogMessage(level = WARN)
//    @Message(id = 1, value = "Could not close handle to mounted %s")
//    void cannotCloseFile(@Cause Throwable cause, VirtualFile file);

//    /**
//     * Logs a warning message indicating the class file, represented by the {@code file} parameter, could not be loaded.
//     *
//     * @param cause the cause of the error.
//     * @param file  the file that could not be loaded.
//     */
//    @LogMessage(level = WARN)
//    @Message(id = 2, value = "Could not load class file %s")
//    void cannotLoadClassFile(@Cause Throwable cause, VirtualFile file);

//    /**
//     * Logs a warning message indicating there was an exception closing the file.
//     *
//     * @param cause the cause of the error.
//     * @param file  the file that failed to close.
//     */
//    @LogMessage(level = WARN)
//    @Message(id = 3, value = "Exception closing file %s")
//    void errorClosingFile(@Cause Throwable cause, VirtualFile file);

//    /**
//     * Logs a warning message indicating there was a failure to undeploy the file.
//     *
//     * @param cause the cause of the error.
//     * @param file  the file that failed to undeploy.
//     */
//    @LogMessage(level = WARN)
//    @Message(id = 4, value = "Failed to undeploy %s")
//    void failedToUndeploy(@Cause Throwable cause, File file);

//    /**
//     * Logs a warning message indicating the file on the ClassPath could not be found.
//     *
//     * @param file the file that could not be found.
//     */
//    @LogMessage(level = WARN)
//    @Message(id = 5, value = "File on ClassPath could not be found: %s")
//    void fileNotFound(VirtualFile file);

//    /**
//     * Logs a warning message indicating an unknown file type was encountered and is being skipped.
//     *
//     * @param file the file.
//     */
//    @LogMessage(level = WARN)
//    @Message(id = 6, value = "Encountered unknown file type, skipping: %s")
//    void skippingUnknownFileType(VirtualFile file);


//    /**
//     * Creates an exception indicating the file, represented by the {@code fileName} parameter, could not be mounted.
//     */
//    @Message(id = 7, value = "Could not mount file '%s'")
//    RuntimeException cannotMountFile(@Cause Throwable cause, String fileName);

//    /**
//     * Creates an exception indicating the contents of the file could not be read.
//     */
//    @Message(id = 8, value = "Could not read contents of %s")
//    RuntimeException cannotReadContent(@Cause Throwable cause, VirtualFile file);

//    /**
//     */
//    @Message(id = 9, value = "One or more exclusion values must be specified")
//    IllegalArgumentException exclusionValuesRequired();

    //@Message(id = 10, value = "WARNING: Failed to load the specified logmodule %s")
    //String failedToLoadLogModule(ModuleIdentifier moduleId);

    /**
     */
    @Message(id = 11, value = "Invalid JBoss home directory: %s")
    IllegalStateException invalidJBossHome(String jbossHome);

//    /**
//     * Creates an exception indicating the module path is invalid.
//     */
//    @Message(id = 12, value = "Invalid module path: %s")
//    IllegalArgumentException invalidModulePath(String file);

//    /**
//     * Creates an exception indicating the module, represented by the {@code moduleName} parameter, was not a valid
//     * type of {@code File[]}, {@code File}, {@code String[]} or {@code String}.
//     */
//    @Message(id = 13, value = "%s was not of type File[], File, String[] or String, but of type %s")
//    RuntimeException invalidModuleType(String moduleName, Class<?> type);

    /**
     * Creates an exception indicating there was an error in the module loader.
     */
    @Message(id = 14, value = "Cannot load module %s from: %s")
    RuntimeException moduleLoaderError(@Cause Throwable cause, String msg, ModuleLoader moduleLoader);

    /**
     * Creates an exception indicating the variable, represented by the {@code name} parameter, is {@code null}.
     */
    @Message(id = 15, value = "%s is null")
    IllegalArgumentException nullVar(String name);

    /**
//     * Creates an exception indicating the system property could not be found.
//     */
//    @Message(id = 16, value = "Cannot find system property: %s")
//    IllegalStateException systemPropertyNotFound(String key);

    @Message(id = 17, value = "Cannot load embedded server factory: %s")
    IllegalStateException cannotLoadEmbeddedServerFactory(@Cause ClassNotFoundException cause, String className);

    @Message(id = 18, value = "Cannot get reflective method '%s' for: %s")
    IllegalStateException cannotGetReflectiveMethod(@Cause NoSuchMethodException cause, String method, String className);

    @Message(id = 19, value = "Cannot create standalone server using factory: %s")
    IllegalStateException cannotCreateStandaloneServer(@Cause Throwable cause, Method createMethod);

    @Message(id = 20, value = "Cannot setup embedded process")
    IllegalStateException cannotSetupEmbeddedServer(@Cause Throwable cause);

    @Message(id = 21, value = "Cannot start embedded process")
    EmbeddedProcessStartException cannotStartEmbeddedServer(@Cause Throwable cause);

    // TODO This logger method is badly named.
    @Message(id = 22, value = "Cannot invoke '%s' on embedded process")
    IllegalStateException cannotInvokeStandaloneServer(@Cause Throwable cause, String methodName);

    @Message(id = 23, value = "The embedded server is stopping and invocations on the ModelControllerClient are not available")
    IllegalStateException processIsStopping();

    @Message(id = 24, value = "The embedded server is reloading and invocations on the ModelControllerClient are not yet available")
    IllegalStateException processIsReloading();

//    @LogMessage(level = WARN)
//    @Message(id = 25, value = "Unable to configure embedded server logging from %s")
//    void cannotConfigureBootLogging(File loggingProperties);

    @Message(id = 26, value = "Cannot create host controller using factory: %s")
    IllegalStateException cannotCreateHostController(@Cause Throwable cause, Method createMethod);

    @Message(id = 27, value = "The embedded server is stopped and invocations on the ModelControllerClient are not available")
    IllegalStateException processIsStopped();

    @Message(id = 28, value = "Error copying '%s' to '%s' (%s)")
    RuntimeException errorCopyingFile(String absolutePath, String absolutePath2, IOException e);

    @Message(id = 29, value = "-D%s=%s is not a directory")
    IllegalArgumentException propertySpecifiedFileIsNotADirectory(String property, String absolutePath);

    @Message(id = 144, value = "-D%s=%s does not exist")
    IllegalArgumentException propertySpecifiedFileDoesNotExist(String property, String absolutePath);

    @Message(id = 143, value = "No directory called '%s' exists under '%s'")
    IllegalArgumentException embeddedServerDirectoryNotFound(String string, String absolutePath);

    @Message(id = 145, value = "The module loader has already been configured. Changing the %s property will have no effect.")
    @LogMessage(level = Logger.Level.WARN)
    void moduleLoaderAlreadyConfigured(String propertyName);

    @Message(id = 146, value = "Failed to restore context %s")
    @LogMessage(level = Logger.Level.ERROR)
    void failedToRestoreContext(@Cause Throwable cause, Context context);
}
