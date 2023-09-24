/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.logging;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONCURRENT_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IN_SERIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.logging.Logger.Level.WARN;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.modules.ModuleLoadException;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MessageLogger(projectCode = "WFLYDC", length = 4)
public interface DomainControllerLogger extends BasicLogger {

    /**
     * A logger with the category of {@code org.jboss.as.domain.controller}.
     * <strong>Usage:</strong> Use this in OSH code related to the resources persisted in domain.xml, or
     * in code specific to the function of the primary Host Controller, e.g. the registration/deregistration
     * of secondary Host Contollers.
     */
    DomainControllerLogger ROOT_LOGGER = Logger.getMessageLogger(DomainControllerLogger.class, "org.jboss.as.domain.controller");

    /**
     * A logger with the category of {@code org.jboss.as.host.controller}.
     * <strong>Usage:</strong> Use this in code related to Host Controller functionality, except
     * for areas described in the documentation of {@link #ROOT_LOGGER}.
     */
    DomainControllerLogger HOST_CONTROLLER_LOGGER = Logger.getMessageLogger(DomainControllerLogger.class, "org.jboss.as.host.controller");

    @LogMessage(level = Level.WARN)
    @Message(id = 1, value = "Ignoring 'include' child of 'socket-binding-group' %s")
    void warnIgnoringSocketBindingGroupInclude(Location location);

    @LogMessage(level = Level.WARN)
    @Message(id = 2, value = "Ignoring 'include' child of 'profile' %s")
    void warnIgnoringProfileInclude(Location location);

    /**
     * Logs a warning message indicating an interruption awaiting the final response from the server, represented by the
     * {@code serverName} parameter, on the host, represented by the {@code hostName} parameter.
     *
     * @param serverName the name of the server.
     * @param hostName   the name of the host.
     */
    @LogMessage(level = Level.INFO)
    @Message(id = 3, value = "Interrupted awaiting final response from server %s on host %s; remote process has been notified to cancel operation")
    void interruptedAwaitingFinalResponse(String serverName, String hostName);

    /**
     * Logs a warning message indicating an exception was caught awaiting the final response from the server,
     * represented by the {@code serverName} parameter, on the host, represented by the {@code hostName} parameter.
     *
     * @param cause      the cause of the error.
     * @param serverName the name of the server.
     * @param hostName   the name of the host.
     */
    @LogMessage(level = Level.WARN)
    @Message(id = 4, value = "Caught exception awaiting final response from server %s on host %s")
    void caughtExceptionAwaitingFinalResponse(@Cause Throwable cause, String serverName, String hostName);

    /**
     * Logs a warning message indicating an interruption awaiting the final response from the host, represented by the
     * {@code hostName} parameter.
     *
     * @param hostName the name of the host.
     */
    @LogMessage(level = Level.INFO)
    @Message(id = 5, value = "Interrupted awaiting final response from host %s; remote process has been notified to cancel operation")
    void interruptedAwaitingFinalResponse(String hostName);

    /**
     * Logs a warning message indicating an exception was caught awaiting the final response from the host, represented
     * by the {@code hostName} parameter.
     *
     * @param cause    the cause of the error.
     * @param hostName the name of the host.
     */
    @LogMessage(level = Level.WARN)
    @Message(id = 6, value = "Caught exception awaiting final response from host %s")
    void caughtExceptionAwaitingFinalResponse(@Cause Throwable cause, String hostName);

    /**
     * Logs a warning message indicating an exception was caught closing the input stream.
     *
     * @param cause the cause of the error.
     */
    @LogMessage(level = Level.WARN)
    @Message(id = 7, value = "Caught exception closing input stream")
    void caughtExceptionClosingInputStream(@Cause Throwable cause);

    /**
     * Logs a warning message indicating the domain model has changed on re-connect and the servers need to be restarted
     * for the changes to take affect.
     *
     * @param servers the servers that need to restart.
     */
    @LogMessage(level = Level.INFO)
    @Message(id = 8, value = "Domain model has changed on re-connect. The following servers will need to be restarted for changes to take affect: %s")
    void domainModelChangedOnReConnect(Set<ServerIdentity> servers);

    /**
     * Logs an error message indicating the class, represented by the {@code className} parameter, caught an exception
     * waiting for the task.
     *
     * @param className     the class name.
     * @param exceptionName the name of the exception caught.
     * @param task          the task.
     */
    @LogMessage(level = Level.ERROR)
    @Message(id = 9, value = "%s caught %s waiting for task %s. Cancelling task")
    void caughtExceptionWaitingForTask(String className, String exceptionName, String task);

    /**
     * Logs an error message indicating the class, represented by the {@code className} parameter, caught an exception
     * waiting for the task and is returning.
     *
     * @param className     the class name.
     * @param exceptionName the name of the exception caught.
     * @param task          the task.
     */
//    @LogMessage(level = Level.ERROR)
//    @Message(id = 10, value = "%s caught %s waiting for task %s; returning")
//    void caughtExceptionWaitingForTaskReturning(String className, String exceptionName, String task);

    /**
     * Logs an error message indicating the content for a configured deployment was unavailable at boot but boot
     * was allowed to proceed because the HC is in admin-only mode.
     *
     * @param contentHash    the content hash that could not be found.
     * @param deploymentName the deployment name.
     */
    @LogMessage(level = Level.ERROR)
    @Message(id = 11, value = "No deployment content with hash %s is available in the deployment content repository for deployment %s. Because this Host Controller is booting in ADMIN-ONLY mode, boot will be allowed to proceed to provide administrators an opportunity to correct this problem. If this Host Controller were not in ADMIN-ONLY mode this would be a fatal boot failure.")
    void reportAdminOnlyMissingDeploymentContent(String contentHash, String deploymentName);

    @LogMessage(level = Level.WARN)
    @Message(id = 12, value = "failed to set server (%s) into a restart required state")
    void failedToSetServerInRestartRequireState(String serverName);

    /**
     * Creates an exception message indicating this host is a secondary and cannot accept registrations from other secondary host controllers.
     *
     * @return a message for the error.
     */
    @Message(id = 13, value = "Registration of remote hosts is not supported on secondary host controllers")
    String slaveControllerCannotAcceptOtherSlaves();

    /**
     * Creates an exception message indicating this host is in admin mode and cannot accept registrations from other
     * slaves.
     *
     * @param runningMode the host controller's current running mode
     *
     * @return a message for the error.
     */
    @Message(id = 14, value = "The primary host controller cannot register secondary host controllers as its current running mode is '%s'")
    String adminOnlyModeCannotAcceptSlaves(RunningMode runningMode);

    /**
     * Creates an exception message indicating a host cannot register because another host of the same name is already
     * registered.
     *
     * @param secondaryName the name of the secondary host controller
     *
     * @return a message for the error.
     */
    @Message(id = 15, value = "There is already a registered host named '%s'")
    String slaveAlreadyRegistered(String secondaryName);

    /**
     * Creates an exception message indicating that a parent is missing a required child.
     *
     * @param parent     the name of the parent element
     * @param child      the name of the missing child element
     * @param parentSpec the complete string representation of the parent element
     *
     * @return the error message
     */
    @Message(id = 16, value = "%s is missing %s: %s")
    String requiredChildIsMissing(String parent, String child, String parentSpec);

    /**
     * Creates an exception message indicating that a parent recognizes only the specified children.
     *
     * @param parent     the name of the parent element
     * @param children   recognized children
     * @param parentSpec the complete string representation of the parent element
     *
     * @return the error message
     */
    @Message(id = 17, value = "%s recognizes only %s as children: %s")
    String unrecognizedChildren(String parent, String children, String parentSpec);

    /**
     * Creates an exception message indicating that in-series is missing groups.
     *
     * @param rolloutPlan string representation of a rollout plan
     *
     * @return the error message
     */
    @Message(id = 18, value = IN_SERIES + " is missing groups: %s")
    String inSeriesIsMissingGroups(String rolloutPlan);

    /**
     * Creates an exception message indicating that server-group expects one and only one child.
     *
     * @param rolloutPlan string representation of a rollout plan
     *
     * @return the error message
     */
    @Message(id = 19, value = SERVER_GROUP + " expects one and only one child: %s")
    String serverGroupExpectsSingleChild(String rolloutPlan);

    /**
     * Creates an exception message indicating that one of the groups in rollout plan does not define neither
     * server-group nor concurrent-groups.
     *
     * @param rolloutPlan string representation of a rollout plan
     *
     * @return the error message
     */
    @Message(id = 20, value = "One of the groups does not define neither " + SERVER_GROUP + " nor " + CONCURRENT_GROUPS + ": %s")
    String unexpectedInSeriesGroup(String rolloutPlan);

    /**
     * A message indicating an unexplained failure.
     *
     * @return the message.
     */
    @Message(id = 21, value = "Unexplained failure")
    String unexplainedFailure();

    /**
     * A message indicating the operation failed or was rolled back on all servers.
     *
     * @return the message.
     */
    @Message(id = 22, value = "Operation failed or was rolled back on all servers.")
    String operationFailedOrRolledBack();

    /**
     * A message indicating an interruption waiting for the result from the server.
     *
     * @param server the server.
     *
     * @return the message.
     */
    @Message(id = 23, value = "Interrupted waiting for result from server %s")
    String interruptedAwaitingResultFromServer(ServerIdentity server);

    /**
     * A message indicating an exception occurred getting the result from the server.
     *
     * @param server  the server.
     * @param message the error message.
     *
     * @return the message.
     */
    @Message(id = 24, value = "Exception getting result from server %s: %s")
    String exceptionAwaitingResultFromServer(ServerIdentity server, String message);

    /**
     * A message indicating an invalid rollout plan. The {@code modelNode} is not a valid child of the node represented
     * by the {@code nodeName} parameter.
     *
     * @param modelNode the model node.
     * @param nodeName  the name of the node.
     *
     * @return the message.
     */
    @Message(id = 25, value = "Invalid rollout plan. %s is not a valid child of node %s")
    String invalidRolloutPlan(ModelNode modelNode, String nodeName);

    /**
     * A message indicating an invalid rollout plan. The plan operations affect server the server groups represented by
     * the server {@code groups} parameter that are not reflected in the rollout plan.
     *
     * @param groups the server groups that are not reflected in the rollout plan.
     *
     * @return the message.
     */
    @Message(id = 26, value = "Invalid rollout plan. Plan operations affect server groups %s that are not reflected in the rollout plan")
    String invalidRolloutPlan(Set<String> groups);

    /**
     * A message indicating an invalid rollout plan. The server group, represented by the {@code group} parameter,
     * appears more than once in the plan.
     *
     * @param group the server group that appears more than once.
     *
     * @return the message.
     */
    @Message(id = 27, value = "Invalid rollout plan. Server group %s appears more than once in the plan.")
    String invalidRolloutPlanGroupAlreadyExists(String group);

    /**
     * A message indicating an invalid rollout plan. The server group, represented by the {@code name} parameter, has an
     * invalid value and must be between 0 and 100.
     *
     * @param name         the name of the group.
     * @param propertyName the name of the property.
     * @param value        the invalid value.
     *
     * @return the message.
     */
    @Message(id = 28, value = "Invalid rollout plan. Server group %s has a %s value of %s; must be between 0 and 100.")
    String invalidRolloutPlanRange(String name, String propertyName, int value);

    /**
     * A message indicating an invalid rollout plan. The server group, represented by the {@code name} parameter, has an
     * invalid value and cannot be less than 0.
     *
     * @param name         the name of the group.
     * @param propertyName the name of the property.
     * @param value        the invalid value.
     *
     * @return the message.
     */
    @Message(id = 29, value = "Invalid rollout plan. Server group %s has a %s value of %s; cannot be less than 0.")
    String invalidRolloutPlanLess(String name, String propertyName, int value);

    /**
     * A message indicating an interruption waiting for the result from host.
     *
     * @param name the name of the host.
     *
     * @return the message.
     */
    @Message(id = 30, value = "Interrupted waiting for result from host %s")
    String interruptedAwaitingResultFromHost(String name);

    /**
     * A message indicating an exception occurred getting the result from the host.
     *
     * @param name    the name of the host.
     * @param message the error message.
     *
     * @return the message.
     */
    // Disabled as part of WFCORE-378
//    @Message(id = 31, value = "Exception getting result from host %s: %s")
//    String exceptionAwaitingResultFromHost(String name, String message);

    /**
     * A message indicating the operation, represented by the {@code operation} parameter, for the {@code address} can
     * only be handled by the domain controller and this host is not the domain controller.
     *
     * @param operation the operation.
     * @param address   the address the operation was to be executed on.
     *
     * @return the message.
     */
    @Message(id = 32, value = "Operation %s for address %s can only be handled by the " +
            "Domain Controller; this host is not the Domain Controller")
    String masterDomainControllerOnlyOperation(String operation, PathAddress address);

    /**
     * An exception indicating the operation targets a host, but the host is not registered.
     *
     * @param name the name of the host.
     *
     * @return the exception.
     */
    @Message(id = 33, value = "Operation targets host %s but that host is not registered")
    OperationFailedException invalidOperationTargetHost(String name);

    /**
     * An exception indicating an exception was caught storing the deployment content.
     *
     * @param exceptionName the name of the caught exception.
     * @param exception     the exception.
     *
     * @return the exception.
     */
    @Message(id = 34, value = "Caught %s storing deployment content -- %s")
    OperationFailedException caughtExceptionStoringDeploymentContent(String exceptionName, Throwable exception);

    /**
     * Creates an exception indicating an unexpected initial path key.
     *
     * @param key the unexpected key.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 35, value = "Unexpected initial path key %s")
    IllegalStateException unexpectedInitialPathKey(String key);

    /**
     * A message indicating the stream is {@code null} at the index.
     *
     * @param index the index.
     *
     * @return the message.
     */
    @Message(id = 36, value = "Null stream at index %d")
    String nullStream(int index);

    /**
     * A message indicating the byte stream is invalid.
     *
     * @return the message.
     */
    @Message(id = 37, value = "Invalid byte stream.")
    String invalidByteStream();

    /**
     * A message indicating the url stream is invalid.
     *
     * @return the message.
     */
    @Message(id = 38, value = "Invalid url stream.")
    String invalidUrlStream();

    /**
     * A message indicating that only 1 piece of content is currently supported. See JBAS-9020.
     *
     * @return the message.
     */
    @Message(id = 39, value = "Only 1 piece of content is currently supported (AS7-431)")
    String as7431();

    /**
     * A message indicating no deployment content with the hash is available in the deployment content repository.
     *
     * @param hash the hash.
     *
     * @return the message.
     */
    @Message(id = 40, value = "No deployment content with hash %s is available in the deployment content repository.")
    String noDeploymentContentWithHash(String hash);

    /**
     * A message indicating a secondary host controller cannot accept deployment content uploads.
     *
     * @return the message.
     */
    @Message(id = 41, value = "A secondary Host Controller cannot accept deployment content uploads")
    String slaveCannotAcceptUploads();

    /**
     * A message indicating no deployment content with the name was found.
     *
     * @param name the name of the deployment.
     *
     * @return the message.
     */
    @Message(id = 42, value = "No deployment with name %s found")
    String noDeploymentContentWithName(String name);

    /**
     * A message indicating the deployment cannot be removed from the domain as it is still used by the server groups.
     *
     * @param name   the name of the deployment.
     * @param groups the server groups using the deployment.
     *
     * @return the message.
     */
    @Message(id = 43, value = "Cannot remove deployment %s from the domain as it is still used by server groups %s")
    String cannotRemoveDeploymentInUse(String name, List<String> groups);

    /**
     * A message indicating the {@code name} has an invalid value.
     *
     * @param name     the name of the attribute.
     * @param value    the invalid value.
     * @param maxIndex the maximum index.
     *
     * @return the message.
     */
    @Message(id = 44, value = "Invalid '%s' value: %d, the maximum index is %d")
    String invalidValue(String name, int value, int maxIndex);

    /**
     * A message indicating the url is not valid.
     *
     * @param url     the invalid url.
     * @param message an error message.
     *
     * @return the message.
     */
    @Message(id = 45, value = "%s is not a valid URL -- %s")
    String invalidUrl(String url, String message);

    /**
     * A message indicating an error occurred obtaining the input stream from the URL.
     *
     * @param url     the invalid url.
     * @param message an error message.
     *
     * @return the message.
     */
    @Message(id = 46, value = "Error obtaining input stream from URL %s -- %s")
    String errorObtainingUrlStream(String url, String message);

    /**
     * A message indicating an invalid content declaration.
     *
     * @return the message.
     */
    @Message(id = 47, value = "Invalid content declaration")
    String invalidContentDeclaration();

    // id = 48; redundant parameter null check message

    /**
     * A message indicating the operation, represented by the {@code opName} parameter, cannot be used with the same
     * value for the parameters represented by {@code param1} and {@code param2}.
     *
     * @param opName         the operation name.
     * @param param1         the first parameter.
     * @param param2         the second parameter.
     * @param redeployOpName the redeploy operation name.
     * @param replaceOpName  the replace operation name.
     *
     * @return the message.
     */
    @Message(id = 49, value = "Cannot use %s with the same value for parameters %s and %s. " +
            "Use %s to redeploy the same content or %s to replace content with a new version with the same name.")
    String cannotUseSameValueForParameters(String opName, String param1, String param2, String redeployOpName, String replaceOpName);

    /**
     * A message indicating the deployment is already started.
     *
     * @param name the name of the deployment.
     *
     * @return the message.
     */
    @Message(id = 50, value = "Deployment %s is already started")
    String deploymentAlreadyStarted(String name);

    /**
     * A message indicating the {@code value} for the {@code name} is unknown.
     *
     * @param name  the name.
     * @param value the value.
     *
     * @return the message.
     */
    @Message(id = 51, value = "Unknown %s %s")
    String unknown(String name, String value);

    /**
     * Creates an exception indicating the server group is unknown.
     *
     * @param serverGroup the unknown server group.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 52, value = "Unknown server group %s")
    IllegalStateException unknownServerGroup(String serverGroup);

    /**
     * Creates an exception indicating the server is unknown.
     *
     * @param server the unknown serve.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 53, value = "Unknown server %s")
    IllegalStateException unknownServer(ServerIdentity server);

    /**
     * Creates an exception indicating the code is invalid.
     *
     * @param code the invalid code.
     *
     * @return an {@link IllegalArgumentException} for the error.
     */
    @Message(id = 54, value = "Invalid code %d")
    IllegalArgumentException invalidCode(int code);

    /**
     * Creates an exception indicating the hash does not refer to any deployment.
     *
     * @param hash the invalid hash.
     *
     * @return an {@link IllegalArgumentException} for the error.
     */
    @Message(id = 55, value = "Repository does not contain any deployment with hash %s")
    IllegalStateException deploymentHashNotFoundInRepository(String hash);

    /**
     * Creates an exception indicating an unexpected number of deployments.
     *
     * @param i number of deployments found
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 56, value = "Expected only one deployment, found %d")
    IllegalStateException expectedOnlyOneDeployment(int i);

    /**
     * Creates an exception indicating no profile could be found with the given name
     *
     * @param profile the profile name
     *
     * @return an {@link OperationFailedException} for the error.
     */
    @Message(id = 57, value = "No profile called: %s")
    OperationFailedException noProfileCalled(String profile);

    /**
     * Error message indicating the content for a configured deployment was unavailable at boot, which is a fatal error.
     *
     *
     *
     * @param contentHash    the content hash that could not be found.
     *
     * @param deploymentName the deployment name.
     * @return the error message
     */
    @Message(id = 58, value = "No deployment content with hash %s is available in the deployment content repository for deployment '%s'. This is a fatal boot error. To correct the problem, either restart with the --admin-only switch set and use the CLI to install the missing content or remove it from the configuration, or remove the deployment from the xml configuraiton file and restart.")
    String noDeploymentContentWithHashAtBoot(String contentHash, String deploymentName);

    @Message(id = 59, value = "Failed to load module '%s'.")
    OperationFailedException failedToLoadModule(@Cause ModuleLoadException e,String module);

    /**
     * Warning messages when a transformer detects that the Jakarta Server Faces subsystem uses a non-default value to setup on a legacy host controller.
     *
     * @param slot the non-default value of the slot attribute
     * @return the message
     */
    @Message(id = 60, value = "Invalid Jakarta Server Faces slot value: '%s'. The host controller is not able to use a Jakarta Server Faces slot value different from its default. This resource will be ignored on that host")
    String invalidJSFSlotValue(String slot);

    /**
     * Warning messages when a transformer detects that an operation defines unknown attributes for a legacy subsystem.
     *
     * @param attributes the name of the attributes unknown from the legacy version
     * @return the message
     */
    @Message(id = 61, value = "Operation '%s' fails because the attributes are not known from the subsytem '%s' model version '%s': %s")
    String unknownAttributesFromSubsystemVersion(String operationName, String subsystemName, ModelVersion version, Collection<String> attributes);

    @Message(id = 62, value = "No socket-binding-group named: %s")
    OperationFailedException noSocketBindingGroupCalled(String socketBindingGroup);

    @Message(id = 63, value = "There is already a deployment called %s with the same runtime name %s on server group %s")
    OperationFailedException runtimeNameMustBeUnique(String existingDeployment, String runtimeName, String serverGroup);

    @Message(id = 64, value = "Cannot remove server-group '%s' since it's still in use by servers %s")
    OperationFailedException cannotRemoveUsedServerGroup(String group, Set<String> servers);

    @Message(id = 65, value = "Wildcard operations are not supported as part of composite operations")
    OperationFailedException unsupportedWildcardOperation();

    @Message(id = 66, value = "Failed to send message: %s")
    String failedToSendMessage(String cause);

    @Message(id = 67, value = "Failed to send response header: %s")
    String failedToSendResponseHeader(String cause);

    @Message(id = 68, value = "Host registration task got interrupted")
    String registrationTaskGotInterrupted();

    @Message(id = 69, value = "Host registration task failed: %s")
    String registrationTaskFailed(String cause);

    @LogMessage(level = Level.INFO)
    @Message(id = 70, value = "%s interrupted awaiting server prepared response(s) -- cancelling updates for servers %s")
    void interruptedAwaitingPreparedResponse(String callerClass, Set<ServerIdentity> servers);

    @LogMessage(level = Level.INFO)
    @Message(id = 71, value = "Interrupted awaiting host prepared response(s) -- cancelling updates for hosts %s")
    void interruptedAwaitingHostPreparedResponse(Set<String> hosts);

    @Message(id = 72, value = "Caught IOException reading uploaded deployment content")
    OperationFailedException caughtIOExceptionUploadingContent(@Cause IOException cause);

    @LogMessage(level = WARN)
    @Message(id = 73, value = "%s deployment has been re-deployed, its content will not be removed. You will need to restart it.")
    void undeployingDeploymentHasBeenRedeployed(String deploymentName);

    /**
     * A message indicating the operation failed or was rolled back on all servers,.
     *
     * @return the message.
     */
    @Message(id = 74, value = "Operation failed or was rolled back on all servers. Server failures:")
    String operationFailedOrRolledBackWithCause();

    @Message(id = 75, value = "Cannot synchronize the model due to missing extensions: %s")
    OperationFailedException missingExtensions(Set<String> missingExtensions);

    @Message(id = 76, value = "Duplicate included profile '%s'")
    XMLStreamException duplicateProfileInclude(String name);

    @Message(id = 77, value = "Duplicate included socket binding group '%s'")
    XMLStreamException duplicateSocketBindingGroupInclude(String s);

    @Message(id = 78, value = "The profile clone operation is not available on the host '%s'. To be able to use it in a " +
            "domain containing older secondary hosts which do not support the profile clone operation, you need to either: " +
            "a) Make sure that all older secondary hosts with a model version smaller than 4.0.0 " +
            "ignore the cloned profile and the profile specified in the 'to-profile' parameter. " +
            "b) Reload the domain controller into admin-only mode, perform the clone, then reload the domain controller " +
            "into normal mode again, and check whether the secondary hosts need reloading.")
    String cloneOperationNotSupportedOnHost(String hostName);

    @LogMessage(level = Level.INFO)
    @Message(id = 79, value = "Timed out after %d ms awaiting host prepared response(s) from hosts %s -- cancelling updates for hosts %s")
    void timedOutAwaitingHostPreparedResponses(long timeout, Set<String> timeoutHosts, Set<String> allHosts);

    @Message(id = 80, value = "Timed out after %d ms awaiting host prepared response(s) -- remote host %s has been notified to cancel operation")
    String timedOutAwaitingHostPreparedResponse(long timeout, String host);

    @LogMessage(level = Level.INFO)
    @Message(id = 81, value = "Timed out after %d ms awaiting final response from host %s; remote process has been notified to cancel operation")
    void timedOutAwaitingFinalResponse(long timeout, String hostName);

    @LogMessage(level = Level.INFO)
    @Message(id = 82, value = "%s timed out after %d ms awaiting server prepared response(s) -- cancelling updates for servers %s")
    void timedOutAwaitingPreparedResponse(String callerClass, long timeout, Set<ServerIdentity> servers);

    @LogMessage(level = Level.INFO)
    @Message(id = 83, value = "Timed out after %d ms awaiting final response from server %s on host %s; remote process has been notified to cancel operation")
    void timedOutAwaitingFinalResponse(int patient, String serverName, String hostName);

    @Message(id = 84, value = "Cannot explode a deployment in a self-contained server")
    OperationFailedException cannotExplodeDeploymentOfSelfContainedServer();

    @Message(id = 85, value = "Cannot explode an unmanaged deployment")
    OperationFailedException cannotExplodeUnmanagedDeployment();

    @Message(id = 86, value = "Cannot explode an already exploded deployment")
    OperationFailedException cannotExplodeAlreadyExplodedDeployment();

    @Message(id = 87, value = "Cannot explode an already deployed deployment")
    OperationFailedException cannotExplodeEnabledDeployment();

    @Message(id = 88, value = "Cannot add content to a deployment in a self-contained server")
    OperationFailedException cannotAddContentToSelfContainedServer();

    @Message(id = 89, value = "Cannot add content to an unmanaged deployment")
    OperationFailedException cannotAddContentToUnmanagedDeployment();

    @Message(id = 90, value = "Cannot add content to an unexploded deployment")
    OperationFailedException cannotAddContentToUnexplodedDeployment();

    @Message(id = 91, value = "Cannot remove content from a deployment in a self-contained server")
    OperationFailedException cannotRemoveContentFromSelfContainedServer();

    @Message(id = 92, value = "Cannot remove content from an unmanaged deployment")
    OperationFailedException cannotRemoveContentFromUnmanagedDeployment();

    @Message(id = 93, value = "Cannot remove content from an unexploded deployment")
    OperationFailedException cannotRemoveContentFromUnexplodedDeployment();

    @Message(id = 94, value = "Cannot read content from a deployment in a self-contained server")
    OperationFailedException cannotReadContentFromSelfContainedServer();

    @Message(id = 95, value = "Cannot read content from an unmanaged deployment")
    OperationFailedException cannotReadContentFromUnmanagedDeployment();

    @Message(id = 96, value = "Cannot read content from an unexploded deployment")
    OperationFailedException cannotReadContentFromUnexplodedDeployment();

    @Message(id = 97, value = "Cannot explode a subdeployment of an unexploded deployment")
    OperationFailedException cannotExplodeSubDeploymentOfUnexplodedDeployment();

    @Message(id = 98, value = "The following servers %s are starting; execution of remote management operations is not currently available")
    OperationFailedException serverManagementUnavailableDuringBoot(String serverNames);
}
