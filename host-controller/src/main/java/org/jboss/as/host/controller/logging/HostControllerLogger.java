/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.logging;

import static org.jboss.logging.Logger.Level.FATAL;
import static org.jboss.logging.Logger.Level.INFO;
import static org.jboss.logging.Logger.Level.WARN;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutionException;
import javax.security.sasl.SaslException;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.host.controller.discovery.DiscoveryOption;
import org.jboss.as.host.controller.model.host.AdminOnlyDomainConfigPolicy;
import org.jboss.as.host.controller.model.jvm.JvmType;
import org.jboss.as.protocol.mgmt.RequestProcessingException;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.logging.annotations.Param;
import org.jboss.remoting3.Channel;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@SuppressWarnings("DefaultAnnotationParam")
@MessageLogger(projectCode = "WFLYHC", length = 4)
public interface HostControllerLogger extends BasicLogger {

    /**
     * A logger with a category of {@code org.jboss.as.host.controller}.
     * <strong>Usage:</strong> Use this in code related to Host Controller functionality, except
     * for areas described in the documentation of {@link #DOMAIN_LOGGER}.
     */
    HostControllerLogger ROOT_LOGGER = Logger.getMessageLogger(HostControllerLogger.class, "org.jboss.as.host.controller");

    /**
     * A logger with a category of {@code org.jboss.as.domain.controller}.
     * <strong>Usage:</strong> Use this in OSH code related to the resources persisted in domain.xml, or
     * in code specific to the function of the primary Host Controller, e.g. the registration/deregistration
     * of secondary Host Contollers.
     */
    HostControllerLogger DOMAIN_LOGGER = Logger.getMessageLogger(HostControllerLogger.class, "org.jboss.as.domain.controller");

    /**
     * Logs a warning message indicating the remote domain controller could not connect.
     *
     * @param uri         the URI to which the connection attempt was made
     * @param cause       the cause
     */
    @LogMessage(level = Level.WARN)
    @Message(id = 1, value = "Could not connect to remote domain controller %s")
    void cannotConnect(URI uri, @Cause Exception cause);

    /**
     * Logs an error message indicating this host controller is a secondary and cannot connect to the primary Host Controller.
     *
     * @param e the cause of the error.
     */
    @LogMessage(level = Level.ERROR)
    @Message(id = 2, value = "Could not connect to the domain controller. Error was: %s")
    void cannotConnectToMaster(Exception e);

    /**
     * Logs an informational message indicating the creation of HTTP management server using the network interface.
     *
     * @param interfaceName the interface name.
     * @param port          the port number.
     * @param securePort    the secure port number.
     */
    @LogMessage(level = INFO)
    @Message(id = 3, value = "Creating http management service using network interface (%s) port (%d) securePort (%d)")
    void creatingHttpManagementService(String interfaceName, int port, int securePort);

//    /**
//     * Logs a warning message indicating an error retrieving domain model from the remote domain controller.
//     *
//     * @param hostName     the name of the host.
//     * @param port         the port number.
//     * @param errorMessage the error message.
//     */
//    @LogMessage(level = Level.WARN)
//    @Message(id = 4, value = "Error retrieving domain model from remote domain controller %s:%d: %s")
//    void errorRetrievingDomainModel(String hostName, int port, String errorMessage);

    /**
     * Logs a warning message indicating the existing server is already defined with a different state.
     *
     * @param serverName the name of the server.
     * @param state      the current state.
     */
    @LogMessage(level = Level.WARN)
    @Message(id = 5, value = "Existing server [%s] with status: %s")
    void existingServerWithState(String serverName, ServerStatus state);

//    /**
//     * Logs an error message indicating a failure to create a server process.
//     *
//     * @param cause      the cause of the error.
//     * @param serverName the server name that failed.
//     */
//    @LogMessage(level = Level.ERROR)
//    @Message(id = 6, value = "Failed to create server process %s")
//    void failedToCreateServerProcess(@Cause Throwable cause, String serverName);

//    /**
//     * Logs an error message indicating a failure to send a reconnect message to the server.
//     *
//     * @param cause      the cause of the error.
//     * @param serverName the server name.
//     */
//    @LogMessage(level = Level.ERROR)
//    @Message(id = 7, value = "Failed to send reconnect message to server %s")
//    void failedToSendReconnect(@Cause Throwable cause, String serverName);

    /**
     * Logs an error message indicating a failure to start the server, represented by the {@code serverName} parameter.
     *
     * @param cause      the cause of the error.
     * @param serverName the name of the server.
     */
    @LogMessage(level = Level.ERROR)
    @Message(id = 8, value = "Failed to start server (%s)")
    void failedToStartServer(@Cause Throwable cause, String serverName);

    /**
     * Logs an error message indicating a failure to stop the server, represented by the {@code serverName} parameter.
     *
     * @param cause      the cause of the error.
     * @param serverName the name of the server.
     */
    @LogMessage(level = Level.ERROR)
    @Message(id = 9, value = "Failed to stop server (%s)")
    void failedToStopServer(@Cause Throwable cause, String serverName);

//    /**
//     * Logs a warning message indicating graceful shutdown of servers is not supported.
//     *
//     * @param serverName the name fo the server.
//     */
//    @LogMessage(level = Level.WARN)
//    @Message(id = 10, value = "Graceful shutdown of server %s was requested but is not presently supported. Falling back to rapid shutdown.")
//    void gracefulShutdownNotSupported(String serverName);

    /**
     * Logs a warning message indicating {@literal <permgen>} is being ignored.
     *
     * @param type the jvm type.
     * @param jvm  the jvm.
     */
    @LogMessage(level = Level.WARN)
    @Message(id = 11, value = "Ignoring <permgen> for jvm '%s' type jvm: %s")
    void ignoringPermGen(JvmType type, String jvm);

    /**
     * Logs an error message indicating this host had no domain controller configuration and cannot start if not in
     * {@link org.jboss.as.controller.RunningMode#ADMIN_ONLY} mode.
     */
    @LogMessage(level = Level.ERROR)
    @Message(id = 12, value = "No <domain-controller> configuration was provided and the current running mode ('%s') " +
            "requires access to the Domain Controller host. Startup will be aborted. Use the %s command line argument " +
            "to start in %s mode if you need to start without a domain controller connection and then use the management " +
            "tools to configure one.")
    void noDomainControllerConfigurationProvided(RunningMode currentRunningMode, String adminOnlyCmdLineArg, RunningMode validRunningMode);

    /**
     * Logs a warning message indicating no security realm was defined for the HTTP management service. All access will
     * be unrestricted.
     */
    @LogMessage(level = Level.WARN)
    @Message(id = 13, value = "No security realm defined for http management service, all access will be unrestricted.")
    void httpManagementInterfaceIsUnsecured();

    /**
     * Logs an error message indicating no server with the server name is available.
     *
     * @param serverName the name of the server.
     */
    @LogMessage(level = Level.ERROR)
    @Message(id = 14, value = "No server called %s available")
    void noServerAvailable(String serverName);

    /**
     * Logs an error message indicating the connection to the remote host controller closed.
     */
    @LogMessage(level = Level.WARN)
    @Message(id = 15, value = "Connection to remote host-controller closed. Trying to reconnect.")
    void lostRemoteDomainConnection();

    /**
     * Logs a warning message indicating the option for the jvm was already set and is being ignored.
     *
     * @param option  the option.
     * @param jvm     the jvm.
     * @param element the schema element.
     */
    @LogMessage(level = Level.WARN)
    @Message(id = 16, value = "Ignoring <option value=\"%s\" for jvm '%s' since '%s' was set")
    void optionAlreadySet(String option, String jvm, String element);

//    /**
//     * Logs an informational message indicating a reconnection to the domain controller.
//     */
//    @LogMessage(level = Level.INFO)
//    @Message(id = 17, value = "Reconnected to the domain controller")
//    void reconnectedToMaster();

    /**
     * Logs an informational message indicating the server is being reconnected.
     *
     * @param serverName the name of the server.
     */
    @LogMessage(level = INFO)
    @Message(id = 18, value = "Reconnecting server %s")
    void reconnectingServer(String serverName);

    /**
     * Logs an informational message indicating the host has been registered as a remote secondary host.
     *
     * @param hostName the host name
     * @param productName the product name
     */
    @LogMessage(level = INFO)
    @Message(id = 19, value = "Registered remote secondary host \"%s\", %s")
    void registeredRemoteSlaveHost(String hostName, String productName);

    /**
     * Logs an informational message indicating the server, represented by the {@code name} parameter, is being
     * registered.
     *
     * @param name the name of the server.
     */
    @LogMessage(level = INFO)
    @Message(id = 20, value = "Registering server %s")
    void registeringServer(String name);

    /**
     * Logs an informational message indicating the server, represented by the {@code name} parameter, was registered
     * using the connection represented by the {@code channel} parameter.
     *
     * @param name    the name of the server.
     * @param channel the channel used to register the connection.
     */
    @LogMessage(level = INFO)
    @Message(id = 21, value = "Server [%s] connected using connection [%s]")
    void serverConnected(String name, Channel channel);

//    /**
//     * Logs a warning message indicating graceful shutdown of management request handling of secondary HC to primary HC
//     * communication did not complete.
//     *
//     * @param cause the cause of the error.
//     */
//    @LogMessage(level = Level.WARN)
//    @Message(id = 22, value = "Graceful shutdown of the handler used for messages from other Host Controllers did not cleanly complete but shutdown of the underlying communication channel is proceeding")
//    void serviceShutdownIncomplete(@Cause Throwable cause);

    /**
     * Logs an informational message indicating the server is starting.
     *
     * @param serverName the name of the server that is starting.
     */
    @LogMessage(level = INFO)
    @Message(id = 23, value = "Starting server %s")
    void startingServer(String serverName);

    /**
     * Logs an informational message indicating the server is stopping.
     *
     * @param serverName the name of the server.
     */
    @LogMessage(level = INFO)
    @Message(id = 24, value = "Stopping server %s")
    void stoppingServer(String serverName);

//    /**
//     * Logs a warning message indicating the server is not in the expected state.
//     *
//     * @param serverName the name of the server.
//     * @param expected   the expected state.
//     * @param current    the current state.
//     */
//    @LogMessage(level = Level.WARN)
//    @Message(id = 25, value = "Server %s is not in the expected %s state: %s")
//    void unexpectedServerState(String serverName, ServerState expected, ServerState current);

    /**
     * Logs an informational message indicating the host has been unregistered as a remote secondary host.
     *
     * @param host the host.
     */
    @LogMessage(level = INFO)
    @Message(id = 26, value = "Unregistered remote secondary host \"%s\"")
    void unregisteredRemoteSlaveHost(String host);

    /**
     * Logs an informational message indicating the server, represented by the {@code name} parameter, is being
     * unregistered.
     *
     * @param name the name of the server.
     */
    @LogMessage(level = INFO)
    @Message(id = 27, value = "Unregistering server %s")
    void unregisteringServer(String name);

//    /**
//     * Informal log message indicating the local host registered at the remote domain controller.
//     */
//    @LogMessage(level = INFO)
//    @Message(id = 28, value =  "Registered at domain controller")
//    void registeredAtRemoteHostController();

    /**
     * Informal log message indicating the local host unregistered at the remote domain controller.
     */
    @LogMessage(level = INFO)
    @Message(id = 29, value =  "Unregistered at domain controller")
    void unregisteredAtRemoteHostController();

    @LogMessage(level = Level.WARN)
    @Message(id = 30, value = "Connection to remote host \"%s\" closed unexpectedly")
    void lostConnectionToRemoteHost(String hostId);

    @LogMessage(level = Level.WARN)
    @Message(id = 31, value = "Cannot load the domain model using --backup")
    void invalidRemoteBackupPersisterState();

//    @LogMessage(level = Level.WARN)
//    @Message(id = 32, value = "Cannot store the domain model using --cached-dc")
//    void invalidCachedPersisterState();

    @LogMessage(level = Level.ERROR)
    @Message(id = 33, value = "Caught exception during boot")
    void caughtExceptionDuringBoot(@Cause Exception e);

    @Message(id = 34, value = "Host Controller boot has failed in an unrecoverable manner; exiting. See previous messages for details. %s")
    String unsuccessfulBoot(String append);

    @LogMessage(level = Level.ERROR)
    @Message(id = 35, value = "Installation of the domain-wide configuration has failed. Because the running mode of this Host Controller is ADMIN_ONLY boot has been allowed to proceed. If ADMIN_ONLY mode were not in effect the process would be terminated due to a critical boot failure.")
    void reportAdminOnlyDomainXmlFailure();

//    /**
//     * Logs a warning message indicating graceful shutdown of management request handling of secondary HC to primary HC
//     * communication did not complete within the given timeout period.
//     *
//     * @param timeout the timeout, in ms.
//     */
//    @LogMessage(level = Level.WARN)
//    @Message(id = 36, value = "Graceful shutdown of the handler used for messages from other Host Controllers did not complete within [%d] ms but shutdown of the underlying communication channel is proceeding")
//    void gracefulManagementChannelHandlerShutdownTimedOut(int timeout);

    @LogMessage(level = INFO)
    @Message(id = 37, value="The primary Host Controller has been restarted. Re-registering this secondary Host Controller with the new primary.")
    void masterHostControllerChanged();

    @LogMessage(level = Level.WARN)
    @Message(id = 38, value="The domain controller could not be reached in the last [%d] milliseconds. Re-connecting.")
    void masterHostControllerUnreachable(long timeout);

    @LogMessage(level = INFO)
    @Message(id = 39, value="The secondary Host Controller \"%s\" has been restarted or is attempting to reconnect. Unregistering the current connection to this secondary.")
    void slaveHostControllerChanged(String hostName);

    @LogMessage(level = Level.WARN)
    @Message(id = 40, value="The secondary Host Controller \"%s\" could not be reached in the last [%d] milliseconds. Unregistering.")
    void slaveHostControllerUnreachable(String hostName, long timeout);


    /**
     * A message indicating an argument was expected for the option.
     *
     *
     * @param option the option that expects the argument.
     * @param usageNote the output of method {@link #usageNote(String)}
     * @return the message.
     */
    @Message(id = 41, value = "Argument expected for option %s. %s")
    String argumentExpected(String option, String usageNote);

    /**
     * Creates an exception indicating an attempt was made to set the {@code attributeToSet} when the {@code
     * attributeAlreadySet} was already set.
     *
     * @param attributeToSet      the attribute to set.
     * @param attributeAlreadySet the attribute was already set.
     *
     * @return an {@link IllegalArgumentException} for the error.
     */
    @Message(id = 42, value = "Attempting to set '%s' when '%s' was already set")
    IllegalArgumentException attemptingToSet(String attributeToSet, String attributeAlreadySet);

    /**
     * Creates an exception indicating an inability to connect due to authentication failures.
     *
     * @param cause the cause of the error.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 43, value = "Unable to connect due to authentication failure.")
    IllegalStateException authenticationFailureUnableToConnect(@Cause Throwable cause);

    /**
     * Creates an exception indicating the remote file repository cannot be accessed from the domain controller.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 44, value = "Cannot access a remote file repository from the domain controller")
    IllegalStateException cannotAccessRemoteFileRepository();

    /**
     * Creates an exception indicating the inability to create a local directory.
     *
     * @param path the directory that failed to create.
     *
     * @return an {@link IOException} for the error.
     */
    @Message(id = 45, value = "Unable to create local directory: %s")
    IOException cannotCreateLocalDirectory(File path);

    /**
     * Creates an exception indicating the default address cannot be obtained for communicating with the
     * ProcessController.
     *
     * @param cause          the cause of the error.
     * @param defaultAddress the default address.
     * @param option         the option.
     *
     * @return a {@link RuntimeException} for the error.
     */
    @Message(id = 46, value = "Cannot obtain a valid default address for communicating with " +
            "the ProcessController using either %s or InetAddress.getLocalHost(). Please check your system's " +
            "network configuration or use the %s command line switch to configure a valid address")
    RuntimeException cannotObtainValidDefaultAddress(@Cause Throwable cause, String defaultAddress, String option);

    /**
     * A message indicating the server, represented by the {@code serverName} parameter, cannot restart as it is not
     * currently started.
     *
     * @param serverName the name of the server.
     * @param status     the status of the server.
     *
     * @return the message.
     */
    @Message(id = 47, value = "Cannot restart server %s as it is not currently started; it is %s")
    String cannotRestartServer(String serverName, ServerStatus status);

    /**
     * A message indicating the servers cannot start when the Host Controller is running in the mode represented by the
     * {@code mode} parameter.
     *
     * @param mode the running mode.
     *
     * @return the message.
     */
    @Message(id = 48, value = "Cannot start servers when the Host Controller running mode is %s")
    String cannotStartServersInvalidMode(RunningMode mode);

    /**
     * Creates an exception indicating the close should be managed by the service.
     *
     * @return an {@link UnsupportedOperationException} for the error.
     */
    @Message(id = 49, value = "Close should be managed by the service")
    UnsupportedOperationException closeShouldBeManagedByService();

    /**
     * Creates an exception indicating the configuration persister for the domain model is already initialized.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 50, value = "Configuration persister for domain model is already initialized")
    IllegalStateException configurationPersisterAlreadyInitialized();

    /**
     * Creates an exception indicating an interruption while trying to connect to domain controller.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 51, value = "Interrupted while trying to connect to the domain controller")
    IllegalStateException connectionToMasterInterrupted();

    /**
     * Creates an exception indicating the connection to primary could not be completed within the number of retries and
     * timeout.
     *
     * @param cause   the cause of the error.
     * @param retries the number of retries.
     * @param timeout the timeout in milliseconds..
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 52, value = "Could not connect to the domain controller in %d attempts within %s ms")
    IllegalStateException connectionToMasterTimeout(@Cause Throwable cause, int retries, long timeout);

    /**
     * Creates an exception indicating the server inventory could bot bre retrieved in the time.
     *
     * @param time     the time.
     * @param timeUnit the time unit.
     *
     * @return a {@link RuntimeException} for the error.
     */
    @Message(id = 53, value = "Could not get the server inventory in %d %s")
    RuntimeException couldNotGetServerInventory(long time, String timeUnit);

    /**
     * Creates an exception indicating the entire file was not read.
     *
     * @param missing the missing length.
     *
     * @return an {@link IOException} for the error.
     */
    @Message(id = 54, value = "Did not read the entire file. Missing: %d")
    IOException didNotReadEntireFile(long missing);

    /**
     * Creates an exception indicating there was an error closing down the host.
     *
     * @param cause the cause of the host.
     *
     * @return a {@link RuntimeException} for the error.
     */
    @Message(id = 55, value = "Error closing down host")
    RuntimeException errorClosingDownHost(@Cause Throwable cause);

    /**
     * A message indicating a failure to retrieve the profile operations from the domain controller.
     *
     * @return the message.
     */
    @Message(id = 56, value = "Failed to retrieve profile operations from domain controller")
    String failedProfileOperationsRetrieval();

    /**
     * Creates an exception indicating a failure to get the file from a remote repository.
     *
     * @param cause the cause of the error.
     *
     * @return a {@link RuntimeException} for the error.
     */
    @Message(id = 57, value = "Failed to get file from remote repository")
    RuntimeException failedToGetFileFromRemoteRepository(@Cause Throwable cause);

    /**
     * A message indicating a failure to get the server status.
     *
     * @return the message.
     */
    @Message(id = 58, value = "Failed to get server status")
    String failedToGetServerStatus();

    /**
     * A message indicating a failure to read the authentication key.
     *
     * @param cause the cause of the error.
     *
     * @return the message.
     */
    @Message(id = 59, value = "Failed to read authentication key: %s")
    String failedToReadAuthenticationKey(Throwable cause);

//    /**
//     * Creates an exception indicating there is already a connection for the host.
//     *
//     * @param hostName the name of the host.
//     *
//     * @return an {@link IllegalArgumentException} for the error.
//     */
//    @Message(id = 60, value = "Already have a connection for host %s")
//    IllegalArgumentException hostNameAlreadyConnected(String hostName);

    /**
     * Creates an exception indicating the information provided could be not used to generate a hash.
     *
     * @return an {@link SaslException} for the error.
     */
    @Message(id = 61, value = "Insufficient information to generate hash.")
    SaslException insufficientInformationToGenerateHash();

    /**
     * A message indicating the option is invalid.
     *
     *
     * @param option the invalid option.
     * @param usageNote the output of method {@link #usageNote(String)}
     *
     * @return the message.
     */
    @Message(id = 62, value = "Invalid option '%s'. %s")
    String invalidOption(String option, String usageNote);

    /**
     * Creates an exception indicating an invalid root id.
     *
     * @param rootId the invalid root id.
     *
     * @return a {@link RequestProcessingException} for the error.
     */
    @Message(id = 63, value = "Invalid root id [%d]")
    RequestProcessingException invalidRootId(int rootId);

    /**
     * A message indicating the value is invalid.
     *
     *
     * @param name  the name of the option.
     * @param type  the type for the value.
     * @param value the value.
     * @param usageNote the output of method {@link #usageNote(String)}
     * @return the message.
     */
    @Message(id = 64, value = "Value for %s is not an %s -- %s. %s")
    String invalidValue(String name, String type, Object value, String usageNote);

    /**
     * Creates an exception indicating invocations of the operation, represented by the {@code name} parameter, after
     * HostController boot are not allowed.
     *
     * @param name the name of the operation.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 65, value = "Invocations of %s after HostController boot are not allowed")
    IllegalStateException invocationNotAllowedAfterBoot(String name);

    /**
     * Creates an exception indicating invocations of the operation after HostController boot are not allowed.
     *
     * @param operation the operation.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    String invocationNotAllowedAfterBoot(ModelNode operation);

    /**
     * A message indicating the provided for the option is malformed.
     *
     *
     * @param option the option.
     * @param usageNote the output of method {@link #usageNote(String)}
     * @return the message.
     */
    @Message(id = 66, value = "Malformed URL provided for option %s. %s")
    String malformedUrl(String option, String usageNote);

    /**
     * Creates an exception indicating the need to call the method, represented by the {@code methodName} parameter,
     * before checking the secondary status.
     *
     * @param methodName the name of the method to invoke.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 67, value = "Must call %s before checking for secondary Host Controller status")
    IllegalStateException mustInvokeBeforeCheckingSlaveStatus(String methodName);

    /**
     * Creates an exception indicating the need to call the method, represented by the {@code methodName} parameter,
     * before persisting the domain model.
     *
     * @param methodName the name of the method to invoke.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 68, value = "Must call %s before persisting the domain model")
    IllegalStateException mustInvokeBeforePersisting(String methodName);

//    /**
//     * Creates an exception indicating there is no channel for the host.
//     *
//     * @param hostName the name of the host.
//     *
//     * @return an {@link IllegalArgumentException} for the error.
//     */
//    @Message(id = 69, value = "No channel for host %s")
//    IllegalArgumentException noChannelForHost(String hostName);

//    /**
//     * Creates an exception indicating a host connecting to a remove domain controller must have its name attribute
//     * set.
//     *
//     * @return an {@link IllegalArgumentException} for the error.
//     */
//    @Message(id = 70, value = "A host connecting to a remote domain controller must have its name attribute set")
//    IllegalArgumentException noNameAttributeOnHost();

    /**
     * Creates an exception indicating there is no server inventory.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 71, value = "No server inventory")
    IllegalStateException noServerInventory();

//    /**
//     * Creates an exception indicating the property already exists.
//     *
//     * @param name the name of the property.
//     *
//     * @return an {@link IllegalArgumentException} for the error.
//     */
//    @Message(id = 72, value = "Property %s already exists")
//    IllegalArgumentException propertyAlreadyExists(String name);

//    /**
//     * Creates an exception indicating the property does not exist.
//     *
//     * @param name the name of the property.
//     *
//     * @return an {@link IllegalArgumentException} for the error.
//     */
//    @Message(id = 73, value = "Property %s does not exist")
//    IllegalArgumentException propertyNotFound(String name);

//    /**
//     * Creates an exception indicating the value for the property is {@code null}.
//     *
//     * @param name the name of the property.
//     *
//     * @return an {@link IllegalArgumentException} for the error.
//     */
//    @Message(id = 74, value = "Value for property %s is null")
//    IllegalArgumentException propertyValueNull(String name);

//    /**
//     * Creates an exception indicating the property has a {@code null} value.
//     *
//     * @param name the name of the property.
//     *
//     * @return an {@link IllegalStateException} for the error.
//     */
//    @Message(id = 75, value = "Property %s has a null value")
//    IllegalStateException propertyValueHasNullValue(String name);

    // id = 76; redundant parameter null check message

    /**
     * Creates an exception indicating there is already a registered server with the name represented by the {@code
     * serverName} parameter.
     *
     * @param serverName the name of the server.
     *
     * @return an {@link IllegalArgumentException} for the error.
     */
    @Message(id = 77, value = "There is already a registered server named '%s'")
    IllegalArgumentException serverNameAlreadyRegistered(String serverName);

    /**
     * A message indicating the server, represented by the {@code name} parameter, is still running.
     *
     * @param name the name of the server.
     *
     * @return the message.
     */
    @Message(id = 78, value = "Server (%s) still running")
    String serverStillRunning(String name);

    /**
     * Creates an exception indicating the inability to generate the hash.
     *
     * @param cause the cause of the error.
     *
     * @return an {@link SaslException} for the error.
     */
    @Message(id = 79, value = "Unable to generate hash")
    SaslException unableToGenerateHash(@Cause Throwable cause);

    /**
     * A message indicating the inability to load properties from the URL.
     *
     *
     * @param url the URL.
     * @param usageNote the output of method {@link #usageNote(String)}
     * @return the message.
     */
    @Message(id = 80, value = "Unable to load properties from URL %s. %s")
    String unableToLoadProperties(URL url, String usageNote);

    /**
     * Creates an exception indicating the socket binding group for the server is undefined.
     *
     * @param serverName the name of the server.
     *
     * @return an {@link IllegalArgumentException} for the exception.
     */
    @Message(id = 81, value = "Undefined socket binding group for server %s")
    IllegalArgumentException undefinedSocketBinding(String serverName);

    /**
     * Creates an exception indicating the socket binding group is undefined.
     *
     * @param name the name of the group.
     *
     * @return an {@link IllegalStateException} for the exception.
     */
    @Message(id = 82, value = "Included socket binding group %s is not defined")
    IllegalStateException undefinedSocketBindingGroup(String name);

//    /**
//     * Creates an exception indicating the service state was unexpected.
//     *
//     * @param state the unexpected state.
//     *
//     * @return an {@link IllegalStateException} for the error.
//     */
//    @Message(id = 83, value = "Unexpected state %s")
//    IllegalStateException unexpectedState(ServerState state);

    /**
     * Creates an exception indicating the {@code value} for the {@code name} is unknown.
     *
     * @param name  the name.
     * @param value the value.
     *
     * @return an {@link IllegalArgumentException} for the error.
     */
    @Message(id = 84, value = "Unknown %s %s")
    IllegalArgumentException unknown(String name, Object value);

    /**
     * A message indicating the value is not a known host.
     *
     *
     * @param name  the name of the option.
     * @param value the value.
     * @param usageNote  the output of method {@link #usageNote(String)}
     * @return the message.
     */
    @Message(id = 85, value = "Value for %s is not a known host -- %s. %s")
    String unknownHostValue(String name, Object value, String usageNote);

//    /**
//     * Creates an exception indicating the type is unrecognized.
//     *
//     * @param type the unrecognized type.
//     *
//     * @return an {@link IOException} for the error.
//     */
//    @Message(id = 86, value = "unrecognized type %s")
//    IOException unrecognizedType(byte type);

    /**
     * Creates an exception indication that the Host Controller was already shutdown.
     * @return an {@link Exception} for the error
     */
    @Message(id = 87, value = "Host-Controller is already shutdown.")
    IllegalStateException hostAlreadyShutdown();

//    /**
//     * Creates an exception indicating no server group could be found with the given name
//     *
//     * @param groupName the group name
//     *
//     * @return an {@link OperationFailedException} for the error.
//     */
//    @Message(id = 88, value = "No server-group called: %s")
//    OperationFailedException noServerGroupCalled(String groupName);

//    /**
//     * Creates an exception indicating no server group could be found with the given name
//     *
//     * @param groupName the socket binding group name
//     *
//     * @return an {@link OperationFailedException} for the error.
//     */
//    @Message(id = 89, value = "No socket-binding-group called: %s")
//    OperationFailedException noSocketBindingGroupCalled(String groupName);

    @Message(id = 90, value = "HostControllerEnvironment does not support system property updates")
    UnsupportedOperationException hostControllerSystemPropertyUpdateNotSupported();

    @Message(id = 91, value = "Resources of type %s cannot be ignored")
    OperationFailedException cannotIgnoreTypeHost(String type);

    @Message(id = 92, value = "An '%s' element whose 'type' attribute is '%s' has already been found")
    XMLStreamException duplicateIgnoredResourceType(String element, String value, @Param Location location);

    @Message(id = 93, value = "The JVM input arguments cannot be accessed so system properties passed directly to this Host Controller JVM will not be passed through to server processes. Cause of the problem: %s")
    String cannotAccessJvmInputArgument(Exception cause);

    @Message(id = 94, value = "Missing configuration value for: %s")
    IllegalStateException missingHomeDirConfiguration(String propertyName);

    @Message(id = 95, value = "Home directory does not exist: %s")
    IllegalStateException homeDirectoryDoesNotExist(File f);

    // WFCORE-2223 no longer used
//    @Message(id = 96, value = "Determined modules directory does not exist: %s")
//    IllegalStateException modulesDirectoryDoesNotExist(File f);

    @Message(id = 97, value = "Domain base directory does not exist: %s")
    IllegalStateException domainBaseDirectoryDoesNotExist(File f);

    @Message(id = 98, value = "Domain base directory is not a directory: %s")
    IllegalStateException domainBaseDirectoryIsNotADirectory(File file);

    @Message(id = 99, value = "Configuration directory does not exist: %s")
    IllegalStateException configDirectoryDoesNotExist(File f);

    @Message(id = 100, value = "Domain data directory is not a directory: %s")
    IllegalStateException domainDataDirectoryIsNotDirectory(File file);

    @Message(id = 101, value = "Could not create domain data directory: %s")
    IllegalStateException couldNotCreateDomainDataDirectory(File file);

    @Message(id = 102, value = "Domain content directory is not a directory: %s")
    IllegalStateException domainContentDirectoryIsNotDirectory(File file);

    @Message(id = 103, value = "Could not create domain content directory: %s")
    IllegalStateException couldNotCreateDomainContentDirectory(File file);

    @Message(id = 104, value = "Log directory is not a directory: %s")
    IllegalStateException logDirectoryIsNotADirectory(File f);

    @Message(id = 105, value = "Could not create log directory: %s")
    IllegalStateException couldNotCreateLogDirectory(File f);

    @Message(id = 106, value = "Servers directory is not a directory: %s")
    IllegalStateException serversDirectoryIsNotADirectory(File f);

    @Message(id = 107, value = "Could not create servers directory: %s")
    IllegalStateException couldNotCreateServersDirectory(File f);

    @Message(id = 108, value = "Domain temp directory does not exist: %s")
    IllegalStateException domainTempDirectoryIsNotADirectory(File file);

    @Message(id = 109, value = "Could not create domain temp directory: %s")
    IllegalStateException couldNotCreateDomainTempDirectory(File file);

    /**
     * Creates an exception indicating an inability to connect due to a SSL failure.
     *
     * @param cause the cause of the error.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 110, value = "Unable to connect due to SSL failure.")
    IllegalStateException sslFailureUnableToConnect(@Cause Throwable cause);

    @Message(id = 111, value = "Option '%s' already exists")
    IllegalStateException jvmOptionAlreadyExists(String option);

//    @Message(id = 112, value = "Environment variable '%s' already exists")
//    IllegalStateException envVariableAlreadyExists(String var);

    @Message(id = 113, value="Host controller management version %s.%s is too old, Only %s.%s or higher are supported")
    OperationFailedException unsupportedManagementVersionForHost(int major, int minor, int minMajor, int minMinor);

    @Message(id = 114, value="Failed to add extensions used by the domain. Failure description: %s")
    IllegalStateException failedToAddExtensions(ModelNode failureDescription);

    /**
     * Messaging indicating a command line argument that was supposed to be parseable into a key
     * and value included no value
     * @param argument the argument provided by the user
     * @param usageNote the output of method {@link #usageNote(String)}
     * @return the message
     */
    @Message(id = 115, value="Argument %s has no value. %s")
    String argumentHasNoValue(String argument, String usageNote);

    /**
     * Creates a simple instruction for how to get usage help. Intended to be appended
     * to command line argument parsing error messages.
     *
     * @param command the command (e.g. 'domain' or 'domain.sh') used to launch the process
     * @return the usage note
     */
    @Message(id = Message.NONE, value="Use %s --help for information on valid command line arguments and their syntax.")
    String usageNote(String command);

    @Message(id=116, value="Cannot access S3 file: %s")
    IllegalStateException cannotAccessS3File(String message);

    @Message(id=117, value="Failed to obtain domain controller data from S3 file")
    IllegalStateException failedMarshallingDomainControllerData();

    @Message(id=118, value="Cannot write domain controller data to S3 file: %s")
    IOException cannotWriteToS3File(String message);

    @Message(id=119, value="Cannot access S3 bucket '%s': %s")
    IllegalStateException cannotAccessS3Bucket(String location, String message);

    @Message(id=120, value="Tried all domain controller discovery option(s) but unable to connect")
    IllegalStateException discoveryOptionsFailureUnableToConnect(@Cause Throwable cause);

    @Message(id=121, value="pre_signed_put_url and pre_signed_delete_url must have the same path")
    IllegalStateException preSignedUrlsMustHaveSamePath();

    @Message(id=122, value="pre_signed_put_url and pre_signed_delete_url must both be set or both unset")
    IllegalStateException preSignedUrlsMustBeSetOrUnset();

    @Message(id=123, value="pre-signed url %s must point to a file within a bucket")
    IllegalStateException preSignedUrlMustPointToFile(String preSignedUrl);

    @Message(id=124, value="pre-signed url %s is not a valid url")
    IllegalStateException invalidPreSignedUrl(String preSignedUrl);

    @Message(id=125, value="pre-signed url %s may only have a subdirectory under a bucket")
    IllegalStateException invalidPreSignedUrlLength(String preSignedUrl);

    @Message(id=126, value="Creating location-constrained bucket with unsupported calling-format")
    IllegalArgumentException creatingBucketWithUnsupportedCallingFormat();

    @Message(id=127, value="Invalid location: %s")
    IllegalArgumentException invalidS3Location(String location);

    @Message(id=128, value="Invalid bucket name: %s")
    IllegalArgumentException invalidS3Bucket(String bucket);

    @Message(id=129, value="bucket '%s' could not be accessed (rsp=%d (%s)). Maybe the bucket is owned by somebody else or the authentication failed")
    IOException bucketAuthenticationFailure(String bucket, int httpCode, String responseMessage);

    @Message(id=130, value="Unexpected response: %s")
    IOException unexpectedResponse(String message);

    @Message(id=131, value="HTTP redirect support required")
    RuntimeException httpRedirectSupportRequired();

    @Message(id=132, value = "Unexpected error parsing bucket listing(s)")
    RuntimeException errorParsingBucketListings(@Cause Throwable cause);

    @Message(id=133, value = "Couldn't initialize a SAX driver for the XMLReader")
    RuntimeException cannotInitializeSaxDriver();

    @Message(id=134, value="Cannot instantiate discovery option class '%s': %s")
    IllegalStateException cannotInstantiateDiscoveryOptionClass(String className, String message);

//    /**
//     * Logs a warning message indicating that the secondary Host Controller could not
//     * connect to the remote domain controller and that another discovery option
//     * will be tried.
//     *
//     * @param e the cause of the error.
//     */
//    @LogMessage(level = Level.WARN)
//    @Message(id=135, value = "Could not connect to primary. Trying another domain controller discovery option. Error was: %s")
//    void tryingAnotherDiscoveryOption(Exception e);

//    /**
//     * Logs a warning message indicating that the secondary Host Controller could not
//     * connect to the remote domain controller and that there are no discovery options left.
//     *
//     * @param e the cause of the error.
//     */
//    @LogMessage(level = Level.WARN)
//    @Message(id=136, value = "Could not connect to primary. No domain controller discovery options left. Error was: %s")
//    void noDiscoveryOptionsLeft(Exception e);

    /**
     * Logs an error message indicating that the domain controller could not write its
     * data to the S3 file.
     *
     * @param e the cause of the error.
     */
    @LogMessage(level = Level.ERROR)
    @Message(id=137, value = "Could not write domain controller data to S3 file. Error was: %s")
    void cannotWriteDomainControllerData(Exception e);

    /**
     * Logs an error message indicating that the domain controller could not remove
     * the S3 file.
     *
     * @param e the cause of the error.
     */
    @LogMessage(level = Level.ERROR)
    @Message(id=138, value = "Could not remove S3 file. Error was: %s")
    void cannotRemoveS3File(Exception e);

    // Is not used any more
//    @Message(id=139, value="Invalid value for %s. Must only contain all of the existing discovery options")
//    OperationFailedException invalidDiscoveryOptionsOrdering(String name);

    @Message(id=140, value="Can't execute transactional operation '%s' from the secondary Host Controller")
    IllegalStateException cannotExecuteTransactionalOperationFromSlave(String operationName);

//    @Message(id=141, value="There is no resource called %s")
//    OperationFailedException noResourceFor(PathAddress address);

    @LogMessage(level = Level.ERROR)
    @Message(id=142, value = "Failed to apply domain-wide configuration from the domain controller")
    void failedToApplyDomainConfig(@Cause Exception e);

    @LogMessage(level = Level.ERROR)
    @Message(id=143, value = "Failed to apply domain-wide configuration from the Domain Controller. " +
            "Operation outcome: %s. Failure description %s")
    void failedToApplyDomainConfig(String outcome, ModelNode failureDescription);

    @LogMessage(level = Level.ERROR)
    @Message(id = 144, value = "The host cannot start because it was started in running mode '%s' with no access " +
            "to a local copy of the domain wide configuration policy, the '%s' attribute was set to '%s' and the " +
            "domain wide configuration policy could not be obtained from the Domain Controller host. Startup will be " +
            "aborted. Use the '%s' command line argument to start if you need to start without connecting to " +
            "a domain controller connection.")
    void fetchConfigFromDomainMasterFailed(RunningMode currentRunningMode, String policyAttribute,
                                               AdminOnlyDomainConfigPolicy policy,
                                               String cachedDcCmdLineArg);

    @LogMessage(level = Level.ERROR)
    @Message(id = 145, value = "The host cannot start because it was started in running mode '%s' with no access " +
            "to a local copy of the domain wide configuration policy, and the '%s' attribute was set to '%s'. Startup " +
            "will be aborted. Use the '%s' command line argument to start in running mode '%s'.")
    void noAccessControlConfigurationAvailable(RunningMode currentRunningMode, String policyAttribute,
                                               AdminOnlyDomainConfigPolicy policy,
                                               String cachedDcCmdLineArg, RunningMode desiredRunningMode);


    /**
     * Logs a warning message indicating that the secondary Host Controller could not
     * discover the remote domain controller using the given {@link org.jboss.as.host.controller.discovery.DiscoveryOption}.
     *
     * @param e the cause of the error.
     */
    @LogMessage(level = Level.WARN)
    @Message(id = 146, value = "Could not discover the domain controller using discovery option %s. Error was: %s")
    void failedDiscoveringMaster(DiscoveryOption option, Exception e);

    /**
     * Logs a warning message indicating that there are no discovery options left.
     *
     */
    @LogMessage(level = Level.WARN)
    @Message(id = 147, value = "No domain controller discovery options remain.")
    void noDiscoveryOptionsLeft();

    /**
     * Logs a message indicating that the secondary Host Controller connected with the primary HC.
     *
     * @param uri the URI at which the primary was reached
     */
    @LogMessage(level = INFO)
    @Message(id = 148, value = "Connected to the domain controller at %s")
    void connectedToMaster(URI uri);

    @LogMessage(level = INFO)
    @Message(id = 149, value = "Option %s was set; obtaining domain-wide configuration from %s")
    void usingCachedDC(String configOption, String cachedXmlFile);

    @LogMessage(level = INFO)
    @Message(id = 150, value = "Trying to reconnect to the domain controller.")
    void reconnectingToMaster();

    @LogMessage(level = Level.ERROR)
    @Message(id = 151, value = "No domain controller discovery configuration was provided and the '%s' attribute was " +
            "set to '%s'. Startup will be aborted. Use the %s command line argument to start in %s mode if you need to " +
            "start without a domain controller connection and then use the management tools to configure one.")
    void noDomainControllerConfigurationProvidedForAdminOnly(String policyAttribute, AdminOnlyDomainConfigPolicy policy,
                                                             String cachedDcCmdLineArg, RunningMode desiredRunningMode);

    /**
     * Logs an informational stating the server launch command prefix.
     *
     * @param serverName the name of the server that will be started with launch command prefix.
     * @param launchCommandPrefix the prefixed launch command.
     */
    @LogMessage(level = INFO)
    @Message(id = 152, value = "Server %s will be started with JVM launch command prefix '%s'")
    void serverLaunchCommandPrefix(String serverName, String launchCommandPrefix);

    @Message(id = 153, value = "Channel closed")
    IOException channelClosed();

//    @Message( id = 154, value = "For a secure port to be enabled for the HTTP management interface a security realm to supply the SSLContext must be specified.")
//    OperationFailedException noSecurityRealmForSsl();

//    /**
//     * @param jvmVersion version that jvm returned
//     */
//    @LogMessage(level = Level.WARN)
//    @Message(id = 155, value = "JVM version '%s' unknown, unpredicted behavior may occur")
//    void jvmVersionUnknown(String jvmVersion);

//    @Message(id = 156, value = "failed to resolve interface %s")
//    OperationFailedException failedToResolveInterface(String name);

    @Message( id = 157, value = "Could not create domain auto-start directory: %s")
    IllegalStateException couldNotCreateDomainAutoStartDirectory(Path file, @Cause Throwable cause);

    @LogMessage(level = INFO)
    @Message( id = 158, value = "Error persisting server autostart status")
    void couldNotPersistAutoStartServerStatus(@Cause Throwable cause);

    @Message(id=159, value="Invalid discovery type %s")
    String invalidDiscoveryType(String type);

    @Message(id = 160, value = "Could not read or create the domain UUID in file: %s")
    IllegalStateException couldNotObtainDomainUuid(@Cause Throwable cause, Path file);

//    @Message(id = 161, value = "Model references of type '%s' are missing: %s")
//    OperationFailedException missingReferences(String type, Set<String> missing);

    @Message(id = 162, value = "The binding name '%s' in socket binding group '%s' is not unique. Names must be unique across socket-binding, local-destination-outbound-socket-binding and remote-destination-outbound-socket-binding")
    OperationFailedException bindingNameNotUnique(String name, String groupName);

    @Message(id = 163, value = "Profile '%s' is involved in a cycle")
    OperationFailedException profileInvolvedInACycle(String profile);

    @Message(id = 164, value = "Profile '%s' defines subsystem '%s' which is also defined in its ancestor profile '%s'. Overriding subsystems is not supported")
    OperationFailedException profileAttemptingToOverrideSubsystem(String existingSubsystemProfile, String subsystem, String profileName);

    @Message(id = 165, value = "Socket binding group '%s' is involved in a cycle")
    OperationFailedException socketBindingGroupInvolvedInACycle(String include);

    @Message(id = 166, value = "Socket binding group '%s' defines socket binding '%s' which is also defined in its ancestor socket binding group '%s'. Overriding socket bindings is not supported")
    OperationFailedException socketBindingGroupAttemptingToOverrideSocketBinding(String existingSubsystemProfile, String child, String resourceName);

    @Message(id = 167, value = "Profile '%s' includes profile '%s' and profile '%s'. Both these profiles define subsystem '%s', which is not supported")
    OperationFailedException profileIncludesSameSubsystem(String profile, String include1, String include2, String child);

    @Message(id = 168, value = "Socket binding group '%s' includes socket binding group '%s' and socket binding group '%s'. Both these socket binding groups define socket binding '%s', which is not supported")
    OperationFailedException socketBindingGroupIncludesSameSocketBinding(String socketBindingGroup, String include1, String include2, String child);

    @Message(id = 169, value = "Reload into running mode is not supported with embedded host controller, admin-only=true must be specified.")
    OperationFailedException embeddedHostControllerRestartMustProvideAdminOnlyTrue();

    @Message(id = 170, value = "Error releasing shared lock after host registration for operationID: %s")
    String hostRegistrationCannotReleaseSharedLock(int operationID);

    @LogMessage(level = Level.ERROR)
    @Message( id = 171, value = "Failed getting the response from the suspend listener for server: %s")
    void suspendListenerFailed(@Cause ExecutionException cause, String serverName);

    @LogMessage(level = Level.ERROR)
    @Message( id = 172, value = "Failed executing the suspend operation for server: %s")
    void suspendExecutionFailed(@Cause IOException cause, String serverName);

    @Message(id = 173, value = "It is not possible to use use-current-host-config=false while specifying a host-config")
    OperationFailedException cannotBothHaveFalseUseCurrentHostConfigAndHostConfig();

    @Message(id = 174, value = "It is not possible to use use-current-domain-config=false while specifying a domain-config")
    OperationFailedException cannotBothHaveFalseUseCurrentDomainConfigAndDomainConfig();


    @Message(id = 175, value = "domain-config '%s' specified for reload could not be found")
    OperationFailedException domainConfigForReloadNotFound(String serverConfig);

//    @Message(id = 176, value = "The deprecated parameter %s has been set in addition to the current parameter %s but with different values")
//    OperationFailedException deprecatedAndCurrentParameterMismatch(String deprecated, String current);

    @LogMessage(level = WARN)
    @Message(id = 177, value = "No security realm defined for native management service; all access will be unrestricted.")
    void nativeManagementInterfaceIsUnsecured();

    @LogMessage(level = FATAL)
    @Message(id = 178, value = "Aborting with exit code %d")
    void aborting(int code);

    @LogMessage(level = INFO)
    @Message(id = 179, value = "ProcessController has signalled to shut down; shutting down")
    void shuttingDownInResponseToProcessControllerSignal();

    @LogMessage(level = INFO)
    @Message(id = 180, value = "Shutting down in response to management operation '%s'")
    void shuttingDownInResponseToManagementRequest(String op);

    @LogMessage(level = INFO)
    @Message(id = 181, value = "Host Controller shutdown has been requested via an OS signal")
    void shutdownHookInvoked();

    @LogMessage(level = Level.INFO)
    @Message(id = 182, value = "Timed out after %d ms awaiting server suspend response(s) for server: %s")
    void timedOutAwaitingSuspendResponse(int blockingTimeout, String serverName);

    @Message(id = 183, value = "Timed out after %d ms awaiting server suspend response(s) for server: %s")
    String timedOutAwaitingSuspendResponseMsg(int blockingTimeout, String serverName);

    @LogMessage(level = Level.INFO)
    @Message(id = 184, value = "%s interrupted awaiting server suspend response(s)")
    void interruptedAwaitingSuspendResponse(@Cause InterruptedException cause, String serverName);

    @Message(id = 185, value = "%s interrupted awaiting server suspend response(s)")
    String interruptedAwaitingSuspendResponseMsg(String serverName);

    @Message( id = 186, value = "Failed executing the suspend operation for server: %s")
    String suspendExecutionFailedMsg(String serverName);

    @Message( id = 187, value = "Failed getting the response from the suspend listener for server: %s")
    String suspendListenerFailedMsg(String serverName);

    @LogMessage(level = Level.INFO)
    @Message(id = 188, value = "Timed out after %d ms awaiting server resume response(s) for server: %s")
    void timedOutAwaitingResumeResponse(int blockingTimeout, String serverName);

    @Message(id = 189, value = "Timed out after %d ms awaiting server resume response(s) for server: %s")
    String timedOutAwaitingResumeResponseMsg(int blockingTimeout, String serverName);

    @LogMessage(level = Level.INFO)
    @Message(id = 190, value = "%s interrupted awaiting server resume response(s)")
    void interruptedAwaitingResumeResponse(@Cause InterruptedException cause, String serverName);

    @Message(id = 191, value = "%s interrupted awaiting server resume response(s)")
    String interruptedAwaitingResumeResponseMsg(String serverName);

    @Message( id = 192, value = "Failed executing the resume operation for server: %s")
    String resumeExecutionFailedMsg(String serverName);

    @Message( id = 193, value = "Failed getting the response from the resume listener for server: %s")
    String resumeListenerFailedMsg(String serverName);

    @LogMessage(level = Level.ERROR)
    @Message( id = 194, value = "Failed executing the resume operation for server: %s")
    void resumeExecutionFailed(@Cause IOException cause, String serverName);

    @LogMessage(level = Level.ERROR)
    @Message( id = 195, value = "Failed getting the response from the resume listener for server: %s")
    void resumeListenerFailed(@Cause ExecutionException cause, String serverName);

    @LogMessage(level = Level.ERROR)
    @Message(id = 196, value = "Cannot move the file %s to %s, unable to persist domain configuration changes: %s ")
    void cannotRenameCachedDomainXmlOnBoot(String tmpFilename, String destFilename, String reason);

    @Message(id = 197, value = "If attribute %s is defined an ssl-context must also be defined")
    OperationFailedException attributeRequiresSSLContext(String attribute);

    @LogMessage(level = Level.WARN)
    @Message( id = 198, value = "Server '%s' is unstable and should be stopped or restarted. An unstable server may not stop normally, " +
            "so the 'kill' operation may be required to terminate the server process.")
    void managedServerUnstable(String serverName);

    @LogMessage(level = Level.WARN)
    @Message( id = Message.INHERIT, value = "Server '%s' (managed by host '%s') is unstable and should be stopped or restarted. An unstable server may not stop normally, " +
            "so the 'kill' operation may be required to terminate the server process.")
    void managedServerUnstable(String serverName, String hostName);

    @LogMessage(level = Level.WARN)
    @Message(id = 199, value = "Server '%s' (managed by host '%s') has not responded to an operation request within the configured timeout. This may mean the server has become unstable.")
    void serverSuspected(String serverName, String hostName);

    @LogMessage(level = Level.ERROR)
    @Message(id = 200, value = "Reporting instability of server '%s' to Domain Controller failed.")
    void failedReportingServerInstabilityToMaster(@Cause Exception e, String serverName);

    @Message(id = 201, value = "Error synchronizing the host model with the domain controller model with failure : %s.")
    String hostDomainSynchronizationError(String failureDescription);

    @LogMessage(level = Level.WARN)
    @Message(id = 202, value = "The domain configuration was successfully applied, but reload is required before changes become active.")
    void domainModelAppliedButReloadIsRequired();

    @LogMessage(level = Level.WARN)
    @Message(id = 203, value = "The domain configuration was successfully applied, but restart is required before changes become active.")
    void domainModelAppliedButRestartIsRequired();

    /**
     * Logs a warning message indicating no logging configuration file could be found. Logging may not be configured
     * until the logging subsystem is activated on the server.
     *
     * @param serverName the name of the server the configuration file could not be found for
     */
    @LogMessage(level = Level.WARN)
    @Message(id = 204, value = "No logging configuration file could be found for the servers initial boot. Logging will " +
            "not be configured until the logging subsystem is activated for the server %s")
    void serverLoggingConfigurationFileNotFound(String serverName);

    /**
     * Logs an error message indicating the {@code -Dlogging.configuration} system property could not be set.
     *
     * @param cause      the cause of the error
     * @param serverName the name of the server setting the property failed on
     * @param path       the path to the configuration file
     */
    @LogMessage(level = Level.ERROR)
    @Message(id = 205, value = "An error occurred setting the -Dlogging.configuration property for server %s. Configuration path %s")
    void failedToSetLoggingConfiguration(@Cause Throwable cause, String serverName, File path);

    @Message(id = 206, value = "File %s already exists, you must use --remove-existing-domain-config to overwrite existing files.")
    IllegalStateException cannotOverwriteDomainXmlWithEmpty(String filename);

    @Message(id = 207, value = "File %s already exists, you must use --remove-existing-host-config to overwrite existing files.")
    IllegalStateException cannotOverwriteHostXmlWithEmpty(String filename);

    @Message(id = 208, value = "A host (%s) has already been registered. You must shutdown this host before adding a new one.")
    OperationFailedException cannotAddHostAlreadyRegistered(String existingName);

    @Message(id = 209, value = "Host name may not be null.")
    OperationFailedException nullHostName();

    @Message(id = 210, value = "A secondary Host Controller may not be added using add(). Please add a host, omitting this parameter, and configure the remote domain controller using write-attribute.")
    OperationFailedException cannotAddSlaveHostAfterBoot();

    @Message(id = 211, value = "Boot configuration validation failed")
    OperationFailedException bootConfigValidationFailed(@Param ModelNode failureDescription);

    @Message(id = 212, value = "Fetch of missing configuration from the Domain Controller failed without explanation. Fetch operation outcome was %s")
    OperationFailedException fetchOfMissingConfigurationFailed(String failedOutcome);

    /**
     * Creates an exception indicating the Java home directory does not exist.
     *
     * @param dir the directory to Java home.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 213, value = "Java home '%s' does not exist.")
    IllegalStateException invalidJavaHome(String dir);

    /**
     * Creates an exception indicating the Java home bin directory does not exist.
     *
     * @param binDir      the bin directory.
     * @param javaHomeDir the Java home directory.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 214, value = "Java home's bin '%s' does not exist. The home directory was determined to be %s.")
    IllegalStateException invalidJavaHomeBin(String binDir, String javaHomeDir);

    /**
     * Creates an exception indicating the Java executable could not be found.
     *
     * @param binDir the directory the executable file should be located.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 215, value = "Could not find java executable under %s.")
    IllegalStateException cannotFindJavaExe(String binDir);

    /**
     * Creates an exception inidcating the module options is not allowed.
     *
     * @param option the option that is not allowed
     *
     * @return an {@link OperationFailedException} for the error
     */
    @Message(id = 216, value = "The module option %s is not allowed.")
    OperationFailedException moduleOptionNotAllowed(String option);

    @Message(id = 217, value = "Security realms are no longer supported, please migrate references to them from the configuration.")
    XMLStreamException securityRealmReferencesUnsupported();

    @Message(id = 218, value = "No %s installation has been prepared.")
    OperationFailedException noServerInstallationPrepared(String productName);

    @Message(id = 219, value = "Authorization failed for '%s' attempting to connect as a domain server.")
    GeneralSecurityException identityNotAuthorizedAsServer(final String identityName);

    @Message(id = 220, value = "Failed to synchronize server status upon a Host Controller registration. There is no handler registered to manage the operation %s for the server %s.")
    IllegalStateException failedToSyncServerStatus(String opName, String serverAddress);

    @Message(id = 221, value = "%s stability level of domain controller does not match %s stability level of connecting host")
    OperationFailedException incompatibleStability(Stability domainStability, Stability hostStability);

    @Message(id = 222, value = "%s stability level is not supported in %s")
    IllegalArgumentException unsupportedStability(Stability stability, String name);

    @Message(id = 223, value = "%s stability level is only supported on Host Controllers with the same management major and minor version than the Domain Controller. Domain Controller version is %d.%d. Remote Host Controller version is %d.%d")
    OperationFailedException mixedDomainUnsupportedStability(Stability domainStability, int dcMajor, int dcMinor, int hcMajor, int hcMinor);

    ////////////////////////////////////////////////
    //Messages without IDs

    @Message(id = Message.NONE, value = "- Host Controller configuration files in use: %s, %s")
    String configFilesInUse(String domainConfigFile, String hostConfigFile);

    @Message(id = Message.NONE, value = "- Host Controller configuration file in use: %s")
    String configFileInUse(String hostConfigFile);

}