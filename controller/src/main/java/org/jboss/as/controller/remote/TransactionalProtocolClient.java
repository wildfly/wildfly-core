/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.remote;

import java.io.IOException;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;
import org.jboss.remoting3.Attachments;
import org.jboss.threads.AsyncFuture;

/**
 * A transactional protocol client to execute management operations between {@link ModelController}s running on different
 * processes.
 * <p />
 * This client is used by the Domain Controller to send management operations to a Host Controller and by a Host Controller to
 * send management operations to a Managed Server.
 * <p />
 * An implementation of this client uses the communication protocol to keep and coordinate the operation's transaniolabitility
 * between both sides of the model controllers.
 *
 * @author Emanuel Muckenhuber
 */
public interface TransactionalProtocolClient {

    /**
     * Attachment whether the client should send the identity as part of the operation request.
     *
     * DC > HC    : HostControllerRegistrationHandler > RemoteDomainConnection
     * HC > server: HostControllerConnection > ManagedServer
     */
    Attachments.Key<Boolean> SEND_IDENTITY = new Attachments.Key<>(Boolean.class);

    /**
     * Attachment whether the client should send a flag that allows IN-VM operation requests.
     * <p>
     * HC > server: HostControllerConnection > ManagedServer
     */
    Attachments.Key<Boolean> SEND_IN_VM = new Attachments.Key<>(Boolean.class);

    /**
     * Execute an operation. This returns a future for the final result, which will only available after the prepared
     * operation is committed.
     *
     * @param listener the operation listener
     * @param operation the operation
     * @param messageHandler the operation message handler
     * @param attachments the operation attachments
     * @return the future result
     * @throws IOException
     */
    AsyncFuture<OperationResponse> execute(TransactionalOperationListener<Operation> listener, ModelNode operation, OperationMessageHandler messageHandler, OperationAttachments attachments) throws IOException;

    /**
     * Execute an operation. This returns a future for the final result, which will only available after the prepared
     * operation is committed.
     *
     * @param listener the operation listener
     * @param operation the operation
     * @param <T> the operation type
     * @return the future result
     * @throws IOException
     */
    <T extends Operation> AsyncFuture<OperationResponse> execute(TransactionalOperationListener<T> listener, T operation) throws IOException;

    /**
     * The transactional operation listener.
     *
     * @param <T> the operation type
     */
    interface TransactionalOperationListener<T extends Operation> {

        /**
         * Notification that an operation was prepared.
         *
         * @param prepared the prepared operation
         */
        void operationPrepared(PreparedOperation<T> prepared);

        /**
         * Notification that an operation failed.
         *
         * @param operation the operation
         * @param result the operation result
         */
        void operationFailed(T operation, ModelNode result);

        /**
         * Notification that an operation completed.
         *
         * @param operation the operation
         * @param result the final result
         */
        void operationComplete(T operation, OperationResponse result);

    }

    /**
     * An operation wrapper.
     */
    interface Operation {

        /**
         * Get the underlying operation.
         *
         * @return the operation
         */
        ModelNode getOperation();

        /**
         * Get the operation message handler.
         *
         * @return the message handler
         */
        OperationMessageHandler getMessageHandler();

        /**
         * Get the operation attachments.
         *
         * @return the attachments
         */
        OperationAttachments getAttachments();

    }

    /**
     * The prepared result.
     *
     * @param <T> the operation type
     */
    interface PreparedOperation<T extends Operation> extends ModelController.OperationTransaction {

        /**
         * Get the initial operation.
         *
         * @return the operation
         */
        T getOperation();

        /**
         * Get the prepared result.
         *
         * @return the prepared result
         */
        ModelNode getPreparedResult();

        /**
         * Check if prepare failed.
         *
         * @return whether the operation failed
         */
        boolean isFailed();

        /**
         * Check if prepare timed out.
         *
         * @return whether the operation failed due to timeout
         */
        boolean isTimedOut();

        /**
         * Is done.
         *
         * @return whether the operation is complete (done or failed).
         */
        boolean isDone();

        /**
         * Get the final result.
         *
         * @return the final result
         */
        AsyncFuture<OperationResponse> getFinalResult();

    }

}
