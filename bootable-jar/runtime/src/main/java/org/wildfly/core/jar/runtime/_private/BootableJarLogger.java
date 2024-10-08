/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.core.jar.runtime._private;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.jboss.as.version.Stability;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.Logger;
import static org.jboss.logging.Logger.Level.DEBUG;
import static org.jboss.logging.Logger.Level.INFO;
import static org.jboss.logging.Logger.Level.WARN;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.modules.ModuleLoader;

/**
 *
 * @author jdenise
 */
@MessageLogger(projectCode = "WFLYJAR", length = 4)
public interface BootableJarLogger extends BasicLogger {

    /**
     * Default root logger with category of the package name.
     */
    BootableJarLogger ROOT_LOGGER = Logger.getMessageLogger(MethodHandles.lookup(), BootableJarLogger.class, "org.wildfly.jar");

    @LogMessage(level = DEBUG)
    @Message(id = 1, value = "Shutting down")
    void shuttingDown();

    @LogMessage(level = DEBUG)
    @Message(id = 2, value = "Server stopped, exiting")
    void serverStopped();

    @LogMessage(level = DEBUG)
    @Message(id = 3, value = "Server not yet stopped, waiting")
    void serverNotStopped();

    @LogMessage(level = DEBUG)
    @Message(id = 4, value = "Null controller client, exiting")
    void nullController();

    @Message(id = 5, value = "Unexpected exception while shutting down server")
    RuntimeException unexpectedExceptionWhileShuttingDown(@Cause Throwable cause);

    @LogMessage(level = INFO)
    @Message(id = 6, value = "Deployed %s in server")
    void installDeployment(Path dep);

    @LogMessage(level = INFO)
    @Message(id = 7, value = "Installed server and application in %s, took %sms")
    void advertiseInstall(Path home, long duration);

    @LogMessage(level = INFO)
    @Message(id = 8, value = "Server options: %s")
    void advertiseOptions(List<String> options);

    @LogMessage(level = DEBUG)
    @Message(id = 9, value = "Deleting %s dir")
    void deletingHome(Path dep);

    @Message(id = 10, value = "Not an hollow jar, deployment already exists")
    Exception deploymentAlreadyExist();

    @Message(id = 11, value = "Unknown argument %s")
    RuntimeException unknownArgument(String arg);

    @Message(id = 12, value = "File %s doesn't exist")
    RuntimeException notExistingFile(String file);

    @Message(id = 13, value = "Invalid argument %s, no value provided")
    RuntimeException invalidArgument(String arg);

    @Message(id = 14, value = "The server is stopping and invocations on the ModelControllerClient are not available")
    IllegalStateException processIsStopping();

    @Message(id = 15, value = "The server is reloading and invocations on the ModelControllerClient are not yet available")
    IllegalStateException processIsReloading();

    @Message(id = 16, value = "The server is stopped and invocations on the ModelControllerClient are not available")
    IllegalStateException processIsStopped();

    @Message(id = 17, value = "Cannot start server")
    RuntimeException cannotStartServer(@Cause Throwable cause);

    @Message(id = 18, value = "Cannot load module %s from: %s")
    RuntimeException moduleLoaderError(@Cause Throwable cause, String msg, ModuleLoader moduleLoader);

    @LogMessage(level = WARN)
    @Message(id = 19, value = "Cannot restart server, exiting")
    void cantRestartServer();

    @LogMessage(level = WARN)
    @Message(id = 20, value = "Can't delete %s. Exception %s")
    void cantDelete(String path, IOException ioex);

    @LogMessage(level = WARN)
    @Message(id = 21, value = "Cannot register JBoss Modules MBeans, %s")
    void cantRegisterModuleMBeans(Exception ex);

    @Message(id = 22, value = "The PID file %s already exists. This may result in the install directory \"%s\" not being properly deleted.")
    IllegalStateException pidFileAlreadyExists(Path pidFile, Path installDir);

    @LogMessage(level = WARN)
    @Message(id = 23, value = "Failed to start the cleanup processor. This may result in the install directory \"%s\" not being properly deleted.")
    void failedToStartCleanupProcess(@Cause Throwable cause, Path installDir);

    @LogMessage(level = WARN)
    @Message(id = 24, value = "The container has not properly shutdown within %ds. This may result in the install directory \"%s\" not being properly deleted.")
    void cleanupTimeout(long timeout, Path installDir);

    @Message(id = Message.NONE, value = "Set system property jboss.bind.address to the given value")
    String argPublicBindAddress();

    @Message(id = Message.NONE, value = "Set system property jboss.bind.address.<interface> to the given value")
    String argInterfaceBindAddress();

    @Message(id = Message.NONE, value = "Set a system property")
    String argSystem();

    @Message(id = Message.NONE, value = "Display this message and exit")
    String argHelp();

    @Message(id = Message.NONE, value = "Load system properties from the given url")
    String argProperties();

    @Message(id = Message.NONE, value = "Set system property jboss.default.multicast.address to the given value")
    String argDefaultMulticastAddress();

    @Message(id = Message.NONE, value = "Print version and exit")
    String argVersion();

    @Message(id = Message.NONE, value = "Activate the SecurityManager")
    String argSecurityManager();

    @Message(id = Message.NONE, value = "Runs the server using a specific stability level. Possible values: %s, Default = %s")
    String argStability(Set<Stability> levels, Stability defaultLevel);

    @Message(id = Message.NONE, value = "Set a security property")
    String argSecurityProperty();

    @Message(id = Message.NONE, value = "Path to deployment artifact (war,jar,ear or exploded deployment dir) to deploy in hollow jar")
    String argDeployment();

    @Message(id = Message.NONE, value = "Path to directory in which the server is installed. By default the server is installed in TEMP directory.")
    String argInstallation();

    @Message(id = Message.NONE, value = "Display the content of the Galleon configuration used to build this bootable JAR")
    String argDisplayGalleonConfig();

    @Message(id = Message.NONE, value = "Path to a CLI script to execute when starting the Bootable JAR")
    String argCliScript();

    @LogMessage(level = DEBUG)
    @Message(id = 25, value = "Failed to initialize a security provider. Reason: %s")
    void securityProviderFailed(Throwable ex);
}
