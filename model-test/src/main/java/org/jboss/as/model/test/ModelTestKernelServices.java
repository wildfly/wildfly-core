/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.model.test;

import java.io.InputStream;
import java.util.List;

import org.jboss.as.controller.ModelController.OperationTransactionControl;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.transform.OperationTransformer.TransformedOperation;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceContainer;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface ModelTestKernelServices<T extends ModelTestKernelServices<T>> {

    /**
     * Get whether the controller booted successfully
     * @return true if the controller booted successfully
     */
    boolean isSuccessfulBoot();

    /**
     * Get any errors thrown on boot
     * @return the boot error
     */
    Throwable getBootError();

    /**
     * Gets the legacy controller services for the controller containing the passed in model version
     *
     * @param modelVersion the model version of the legacy model controller
     * @throws IllegalStateException if this is not the test's main model controller
     * @throws IllegalStateException if there is no legacy controller containing the version
     */
    T getLegacyServices(ModelVersion modelVersion);

    /**
     * Transforms an operation in the main controller to the format expected by the model controller containing
     * the legacy subsystem
     *
     * @param modelVersion the subsystem model version of the legacy subsystem model controller
     * @param operation the operation to transform
     * @return the transformed operation
     * @throws IllegalStateException if this is not the test's main model controller
     */
    TransformedOperation transformOperation(ModelVersion modelVersion, ModelNode operation) throws OperationFailedException;

    /**
     * Transforms the model to the legacy subsystem model version
     * @param modelVersion the target legacy subsystem model version
     * @return the transformed model
     * @throws IllegalStateException if this is not the test's main model controller
     */
    ModelNode readTransformedModel(ModelVersion modelVersion);

    /**
     * Execute an operation in the  controller containg the passed in version of the subsystem.
     * The operation and results will be translated from the format for the main controller to the
     * legacy controller's format.
     *
     * @param modelVersion the subsystem model version of the legacy subsystem model controller
     * @param op the operation for the main controller
     * @throws IllegalStateException if this is not the test's main model controller
     * @throws IllegalStateException if there is no legacy controller containing the version of the subsystem
     */
    ModelNode executeOperation(final ModelVersion modelVersion, final TransformedOperation op);

    /**
     * Reads the whole model from the model controller without aliases or runtime attributes/resources
     *
     * @return the whole model
     */
    ModelNode readWholeModel();

    /**
     * Reads the whole model from the model controller without runtime attributes/resources
     *
     * @param includeAliases whether to include aliases
     * @return the whole model
     */
    ModelNode readWholeModel(boolean includeAliases);

    /**
     * Reads the whole model from the model controller
     *
     * @param includeAliases whether to include aliases
     * @param includeRuntime whether to include runtime attributes/resources
     * @return the whole model
     */
    ModelNode readWholeModel(boolean includeAliases, boolean includeRuntime);

    /**
     * Gets the service container
     *
     * @return the service container
     */
    ServiceContainer getContainer();

    /**
     * Execute an operation in the model controller
     *
     * @param operation the operation to execute
     * @param inputStreams Input Streams for the operation
     * @return the whole result of the operation
     */
    ModelNode executeOperation(ModelNode operation, InputStream... inputStreams);

    ModelNode executeOperation(ModelNode operation, OperationTransactionControl txControl);

    ModelNode executeForResult(ModelNode operation, InputStream... inputStreams) throws OperationFailedException;

    /**
     * Execute an operation in the model controller, expecting failure.
     *
     * @param operation the operation to execute
     */
    void executeForFailure(ModelNode operation, InputStream... inputStreams);

    /**
     * Reads the persisted subsystem xml
     *
     * @return the xml
     */
    String getPersistedSubsystemXml();

    /**
     * Validates the operations against the description providers in the model controller
     *
     * @param operations the operations to validate
     */
    void validateOperations(List<ModelNode> operations);

    /**
     * Validates the operation against the description providers in the model controller
     *
     * @param operation the operation to validate
     */
    void validateOperation(ModelNode operation);

    void shutdown();

    ImmutableManagementResourceRegistration getRootRegistration();

    /**
     * Get information about error on boot from both BootErrorCollector and BootError
     *
     * @return BootErrorCollector description if any and BootError printed stack trace if any, empty string otherwise
     */
    default String getBootErrorDescription(){
        String errorDescription = "";
        ModelNode result = getBootErrorCollectorFailures();
        if (hasBootErrorCollectorFailures()) {
            errorDescription += "BootErrorCollector failures: " + result.asString() + System.lineSeparator();
        }
        if (getBootError() != null) {
            errorDescription += "BootError: " + getBootError().toString() + getBootError().getStackTrace().toString();
        }
        return errorDescription;
    }

    /**
     * Retrieve failures collected by BootErrorCollector if there are any
     * and in case of OperationFailedException thrown by executeForResult will be outputted
     * the error message containing exception description
     *
     * @return failures from BootErrorCollector or null when retrieved none or undefined failure
     */
    default ModelNode getBootErrorCollectorFailures(){
        ModelNode readBootErrorsOp = Util.createOperation("read-boot-errors", PathAddress.pathAddress(PathElement.pathElement(CORE_SERVICE, MANAGEMENT)));
        ModelNode result = null;
        try {
            result = executeForResult(readBootErrorsOp);
            if (result.asString().equals("undefined")) {
                return null;
            }
        } catch (OperationFailedException e) {
            System.err.println("Error getting BootErrorCollector failures: " + e.toString());
        }
        return result;
    }

    /**
     * Returns whether BootErrorCollector collected any failure
     *
     * @return true if BootErrorCollector collected any failure
     */
    default boolean hasBootErrorCollectorFailures() {
        return getBootErrorCollectorFailures() != null;
    }
}