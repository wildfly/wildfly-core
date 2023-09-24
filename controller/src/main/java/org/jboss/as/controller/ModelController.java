/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.security.Permission;

import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.security.ControllerPermission;
import org.jboss.dmr.ModelNode;

/**
 * Controls reads of and modifications to a management model.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface ModelController {

    /**
     * A {@link org.jboss.as.controller.security.ControllerPermission} needed to access a {@link ModelController} via {@link org.jboss.msc.service.Service#getValue()} or
     * to invoke its methods. The name of the permission is "{@code canAccessModelController}."
     */
    Permission ACCESS_PERMISSION = ControllerPermission.CAN_ACCESS_MODEL_CONTROLLER;

    /**
     * Execute an operation, sending updates to the given handler. This method is not intended to be invoked directly
     * by clients.
     *
     * @param operation the operation to execute
     * @param handler the message handler
     * @param control the transaction control for this operation
     * @param attachments the operation attachments
     * @return the operation result
     *
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    ModelNode execute(ModelNode operation, OperationMessageHandler handler, OperationTransactionControl control, OperationAttachments attachments);

    /**
     * Execute an operation, sending updates to the given handler, and making available in the return value
     * any streams that may have been associated with the response.  This method is not intended to be invoked directly
     * by clients.
     *
     * @param operation the operation to execute
     * @param handler the message handler
     * @param control the transaction control for this operation
     * @return the operation response
     *
     * @throws SecurityException if the caller does not have {@link #ACCESS_PERMISSION}
     */
    OperationResponse execute(Operation operation, OperationMessageHandler handler, OperationTransactionControl control);

    /**
     * A callback interface for the operation's completion status.  Implemented in order to control whether a complete
     * operation is committed or rolled back after it is prepared.
     */
    interface OperationTransactionControl {

        /**
         * Notify that an operation is complete and may be committed or rolled back.
         *
         * <p><strong>It is the responsibility of the user of this {@code OperationTransactionControl} to ensure that
         * {@link OperationTransaction#commit()} or {@link OperationTransaction#rollback()} is eventually called on
         * the provided {@code transaction}.
         * </strong></p>
         *
         * @param transaction the transaction to control the fate of the operation. Cannot be {@code null}
         * @param result the result. Cannot be {@code null}
         * @param context the {@code OperationContext} for the operation that is prepared
         */
        default void operationPrepared(OperationTransaction transaction, ModelNode result, OperationContext context) {
            operationPrepared(transaction, result);
        }

        /**
         * Notify that an operation is complete and may be committed or rolled back.
         *
         * <p><strong>It is the responsibility of the user of this {@code OperationTransactionControl} to ensure that
         * {@link OperationTransaction#commit()} or {@link OperationTransaction#rollback()} is eventually called on
         * the provided {@code transaction}.
         * </strong></p>
         *
         * @param transaction the transaction to control the fate of the operation. Cannot be {@code null}
         * @param result the result. Cannot be {@code null}
         */
        void operationPrepared(OperationTransaction transaction, ModelNode result);

        /**
         * An operation transaction control implementation which always commits the operation.
         */
        OperationTransactionControl COMMIT = new OperationTransactionControl() {
            public void operationPrepared(final OperationTransaction transaction, final ModelNode result) {
                transaction.commit();
            }
        };
    }

    /**
     * An operation transaction.
     */
    interface OperationTransaction {

        /**
         * Commit the operation.
         */
        void commit();

        /**
         * Roll the operation back.
         */
        void rollback();
    }

}
