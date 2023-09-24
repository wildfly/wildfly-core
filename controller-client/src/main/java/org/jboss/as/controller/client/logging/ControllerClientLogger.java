/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.logging;

import static org.jboss.logging.Logger.Level.WARN;

import java.io.IOException;
import java.net.URI;
import java.net.URL;

import org.jboss.as.controller.client.helpers.domain.DeploymentAction.Type;
import org.jboss.as.controller.client.helpers.domain.RollbackCancelledException;
import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@MessageLogger(projectCode = "WFLYCC", length = 4)
public interface ControllerClientLogger extends BasicLogger {

    /**
     * A logger with the default package name.
     */
    ControllerClientLogger ROOT_LOGGER = Logger.getMessageLogger(ControllerClientLogger.class, "org.jboss.as.controller.client");

    /**
     * Creates an exception indicating after starting creation of the rollout plan no deployment actions can be added.
     *
     * @return a {@link IllegalStateException} for the error.
     */
    @Message(id = 1, value = "Cannot add deployment actions after starting creation of a rollout plan")
    IllegalStateException cannotAddDeploymentAction();

    /**
     * Creates an exception indicating no deployment actions can be added after starting the creation of the rollout
     * plan.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 2, value = "Cannot add deployment actions after starting creation of a rollout plan")
    IllegalStateException cannotAddDeploymentActionsAfterStart();

    /**
     * A message indicating that {@code first} cannot be converted to {@code second}.
     *
     * @param first  the type that could not be converted.
     * @param second the type attempting to be converted to.
     *
     * @return the message.
     */
    @Message(id = 3, value = "Cannot convert %s to %s")
    String cannotConvert(String first, String second);

    /**
     * Creates an exception indicating the deployment name could not be derived from the URL.
     *
     * @param url the URL to the deployment.
     *
     * @return an {@link IllegalArgumentException} for the error.
     */
    @Message(id = 4, value = "Cannot derive a deployment name from %s -- use an overloaded method variant that takes a 'name' parameter")
    IllegalArgumentException cannotDeriveDeploymentName(URL url);

    /**
     * Creates an exception indicating the {@code DeploymentPlan} cannot be used because it was not created by this
     * manager.
     *
     * @return an {@link IllegalArgumentException} for the error.
     */
    @Message(id = 5, value = "Cannot use a DeploymentPlan not created by this manager")
    IllegalArgumentException cannotUseDeploymentPlan();

//    /**
//     * Creates an exception indicating the channel is closed.
//     *
//     * @return an {@link IOException} for the error.
//     */
//    @Message(id = 6, value = "Channel closed")
//    IOException channelClosed(@Cause IOException cause);

    /**
     * A message indicating a deployment with the {@code name} is already present in the domain.
     *
     * @param name the name of the deployment.
     *
     * @return the message.
     */
    @Message(id = 7, value = "Deployment with name %s already present in the domain")
    String domainDeploymentAlreadyExists(String name);

    /**
     * The word failed.
     *
     * @return failed.
     */
    @Message(id = 8, value = "failed")
    String failed();

    /**
     * Creates an exception indicating a global rollback is not compatible with a server restart.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 9, value = "Global rollback is not compatible with a server restart")
    IllegalStateException globalRollbackNotCompatible();

    /**
     * Creates an exception indicating the graceful shutdown already configured with a timeout, represented by the
     * {@code timeout} parameter.
     *
     * @param timeout the already configured timeout.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 10, value = "Graceful shutdown already configured with a timeout of %d ms")
    IllegalStateException gracefulShutdownAlreadyConfigured(long timeout);

    /**
     * A message indicating only one version of a deployment with a given unique name can exist in the domain.
     *
     * @param deploymentName the deployment name.
     * @param missingGroups  the missing groups.
     *
     * @return the message.
     */
    @Message(id = 11, value = "Only one version of deployment with a given unique name can exist in the domain. The deployment " +
            "plan specified that a new version of deployment %s replace an existing deployment with the same unique " +
            "name, but did not apply the replacement to all server groups. Missing server groups were: %s")
    String incompleteDeploymentReplace(String deploymentName, String missingGroups);

    /**
     * Creates an exception indicating the action type, represented by the {@code type} parameter, is invalid.
     *
     * @param type the invalid type.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 12, value = "Invalid action type %s")
    IllegalStateException invalidActionType(Type type);

    /**
     * Creates an exception indicating the preceding action was not a
     * {@link org.jboss.as.controller.client.helpers.standalone.DeploymentAction.Type type}.
     *
     * @param type the type that preceding action should be.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 13, value = "Preceding action was not a %s")
    IllegalStateException invalidPrecedingAction(Object type);

    /**
     * Creates an exception indicating the URL is not a valid URI.
     *
     * @param cause the cause of the error.
     * @param url   the URL.
     *
     * @return an {@link IllegalArgumentException} for the error.
     */
    @Message(id = 14, value = "%s is not a valid URI")
    IllegalArgumentException invalidUri(@Cause Throwable cause, URL url);

    /**
     * Creates an exception indicating the value is invalid and must be greater than the {@code minValue}.
     *
     * @param name     the name for the value.
     * @param value    the invalid value.
     * @param minValue the minimum value allowed.
     *
     * @return an {@link IllegalArgumentException} for the error.
     */
    @Message(id = 15, value = "Illegal %s value %d -- must be greater than %d")
    IllegalArgumentException invalidValue(String name, int value, int minValue);

    /**
     * Creates an exception indicating the value is invalid and must be greater than the {@code minValue} and less than
     * the {@code maxValue}.
     *
     * @param name     the name for the value.
     * @param value    the invalid value.
     * @param minValue the minimum value allowed.
     * @param maxValue the maximum value allowed
     *
     * @return an {@link IllegalArgumentException} for the error.
     */
    @Message(id = 16, value = "Illegal %s value %d -- must be greater than %d and less than %d")
    IllegalArgumentException invalidValue(String name, int value, int minValue, int maxValue);

    /**
     * Creates an exception indicating that screen real estate is expensive and displayUnits must be 5 characters or
     * less.
     *
     * @return a {@link RuntimeException} for the error.
     */
    @Message(id = 17, value = "Screen real estate is expensive; displayUnits must be 5 characters or less")
    RuntimeException maxDisplayUnitLength();

//    /**
//     * Creates an exception indicating no active request found for the batch id.
//     *
//     * @param batchId the batch id.
//     *
//     * @return an {@link IOException} for the error.
//     */
//    @Message(id = 18, value = "No active request found for %d")
//    IOException noActiveRequest(int batchId);

    /**
     * A message indicating that no failure details were provided.
     *
     * @return the message.
     */
    @Message(id = 19, value = "No failure details provided")
    String noFailureDetails();

    /**
     * Creates an exception indicating the {@code name} is not configured.
     *
     * @param name the name that is not configured.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 20, value = "No %s is configured")
    IllegalStateException notConfigured(String name);

    // id = 21; redundant parameter null check message

    /**
     * Creates an exception indicating the object is closed.
     *
     * @param name the name of the object.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 22, value = "%s is closed")
    IllegalStateException objectIsClosed(String name);

    /**
     * Creates an exception with the operation outcome.
     *
     * @param outcome the operation outcome.
     *
     * @return a {@link RuntimeException} for the error.
     */
    @Message(id = 23, value = "Operation outcome is %s")
    RuntimeException operationOutcome(String outcome);

    /**
     * Creates an exception indicating operations are not not allowed after content and deployment modifications.
     *
     * @param name the name for the operations.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 24, value = "%s operations are not allowed after content and deployment modifications")
    IllegalStateException operationsNotAllowed(String name);

    /**
     * Creates an exception indicating the rollback was cancelled.
     *
     * @return a {@link RollbackCancelledException} for the error.
     */
    @Message(id = 25, value = "Rollback was cancelled")
    RollbackCancelledException rollbackCancelled();

    /**
     * Creates an exception indicating the rollback was itself rolled back.
     *
     * @return a {@link RollbackCancelledException} for the error.
     */
    @Message(id = 26, value = "Rollback was itself rolled back")
    RollbackCancelledException rollbackRolledBack();

    /**
     * Creates an exception indicating the rollback timed out.
     *
     * @return a {@link RollbackCancelledException} for the error.
     */
    @Message(id = 27, value = "Rollback timed out")
    RollbackCancelledException rollbackTimedOut();

    /**
     * A message indicating a deployment with the {@code name} is already present in the domain.
     *
     * @param name the name of the deployment.
     *
     * @return the message.
     */
    @Message(id = 28, value = "Deployment with name %s already present in the server")
    String serverDeploymentAlreadyExists(String name);

    /**
     * Creates an exception indicating the action type, represented by the {@code type} parameter, is unknown.
     *
     * @param type the unknown type.
     *
     * @return an {@link IllegalStateException} for the error.
     */
    @Message(id = 29, value = "Unknown action type %s")
    IllegalStateException unknownActionType(Object type);

    /**
     * Creates a leak description, used in the controller client to show the original allocation point creating the
     * client.
     *
     * @return the leak description
     */
    @Message(id = 30, value = "Allocation stack trace:")
    LeakDescription controllerClientNotClosed();

    /**
     * Creates an exception indicating the operation was successful and no failure description exists.
     *
     * @return a {@link IllegalArgumentException} for the error
     */
    @Message(id = 31, value = "No failure description as the operation was successful.")
    IllegalArgumentException noFailureDescription();

    /**
     * Creates an exception indicating the operation name was not defined.
     *
     * @return a {@link IllegalArgumentException} for the error
     */
    @Message(id = 32, value = "The operation name was not defined.")
    IllegalArgumentException operationNameNotFound();

    /**
     * Creates an exception indicating the address must be of type {@link org.jboss.dmr.ModelType#LIST list}.
     *
     * @return an {@link IllegalArgumentException} for the error
     */
    @Message(id = 33, value = "The address must be of type ModelType.LIST.")
    IllegalArgumentException invalidAddressType();

    @LogMessage(level = WARN)
    @Message(id = 34, value = "Closing leaked controller client")
    void leakedControllerClient(@Cause Throwable allocationStackTrace);

    @LogMessage(level = WARN)
    @Message(id = 35, value = "Cannot delete temp file %s, will be deleted on exit")
    void cannotDeleteTempFile(String name);

    @Message(id = 36, value = "Stream was closed")
    IOException streamWasClosed();

    @Message(id = 37, value = "Failed to parse the configuration file: %s")
    RuntimeException failedToParseAuthenticationConfig(@Cause Throwable cause, URI location);

    class LeakDescription extends Throwable {
        private static final long serialVersionUID = -7193498784746897578L;

        public LeakDescription() {
            //
        }

        public LeakDescription(String message) {
            super(message);
        }

        @Override
        public String toString() {
            // skip the class-name
            return getLocalizedMessage();
        }
    }

}
