/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;


import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;

/**
 * A proxy controller to be registered with a ModelController.
 * <p/>
 * Proxy controllers apply to a given address in the host ModelController
 * and typically allow access to an external ModelController.
 * <p/>
 * For example if a ProxyController is registered in the host ModelController for the address
 * <code>[a=b,c=d]</code>, then an operation executed in the host ModelController for
 * <code>[a=b]</code> will execute in the host model controller as normal. An operation for
 * <code>[a=b,c=d,x=y]</code> will apply to <code>[x=y]</code> in the model controller
 * pointed to by this proxy controller.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public interface ProxyController {

    /**
     * Get the address where this proxy controller applies to in the host ModelController
     *
     * @return the address where this proxy controller applies.
     */
    PathAddress getProxyNodeAddress();

    /**
     * Execute an operation, sending updates to the given handler and receiving the response via the given
     * {@link ModelController.OperationTransactionControl}.   When this operation returns, either the
     * {@link ProxyOperationControl#operationPrepared(ModelController.OperationTransaction, org.jboss.dmr.ModelNode)}
     * or the {@link ProxyOperationControl#operationFailed(org.jboss.dmr.ModelNode)} callbacks on the given  {@code control}
     * will have been invoked.
     *  @param operation the operation to execute. Cannot be {@code null}
     * @param handler the message handler. May be {@code null}
     * @param control the callback handler for this operation. Cannot be {@code null}
     * @param attachments the operation attachments. May be {@code null}
     * @param blockingTimeout control for maximum period any blocking operations can block. Cannot be {@code null}
     */
    void execute(ModelNode operation, OperationMessageHandler handler, ProxyOperationControl control, OperationAttachments attachments, BlockingTimeout blockingTimeout);

    /**
     * Gets the {@link ModelVersion} of the kernel management API exposed by the proxied process.
     *
     * @return the model version. Will not be {@code null}
     */
    default ModelVersion getKernelModelVersion() {
        return ModelVersion.CURRENT;
    }

    interface ProxyOperationControl extends ModelController.OperationTransactionControl {

        /**
         * Handle the result of an operation whose execution failed before
         * {@link ModelController.OperationTransactionControl#operationPrepared(ModelController.OperationTransaction, ModelNode)}
         * could be invoked.
         *
         * @param response the response to the operation.
         */
        void operationFailed(ModelNode response);

        /**
         * Handle the final result of an operation, following invocation of
         * {@link ModelController.OperationTransactionControl#operationPrepared(ModelController.OperationTransaction, ModelNode)}.
         * This provides the final response, including any changes made as a result of rolling back the transaction.
         * <p>
         * This callback will have been invoked by the time the call made to {@link ModelController.OperationTransaction#commit() commit()}
         * or {@link ModelController.OperationTransaction#rollback() rollback()} on the {@code OperationTransaction} provided
         * to {@link #operationPrepared(ModelController.OperationTransaction, ModelNode)} has returned.
         * </p>
         *
         * @param response the response
         */
        void operationCompleted(OperationResponse response);
    }

}
