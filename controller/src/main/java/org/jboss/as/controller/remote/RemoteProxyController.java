/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.remote;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CANCELLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.BlockingTimeout;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ProxyOperationAddressTranslator;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.threads.AsyncFuture;

/**
 * Remote {@link ProxyController} implementation.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Emanuel Muckenhuber
 */
public class RemoteProxyController implements ProxyController {

    private final PathAddress pathAddress;
    private final ProxyOperationAddressTranslator addressTranslator;
    private final TransactionalProtocolClient client;
    private final ModelVersion targetKernelVersion;

    private RemoteProxyController(final TransactionalProtocolClient client, final PathAddress pathAddress,
                                  final ProxyOperationAddressTranslator addressTranslator,
                                  final ModelVersion targetKernelVersion) {
        this.client = client;
        this.pathAddress = pathAddress;
        this.addressTranslator = addressTranslator;
        this.targetKernelVersion = targetKernelVersion;
    }

    /**
     * Create a new remote proxy controller.
     *
     * @param client the transactional protocol client
     * @param pathAddress the path address
     * @param addressTranslator the address translator
     * @param targetKernelVersion the {@link ModelVersion} of the kernel management API exposed by the proxied process
     * @return the proxy controller
     */
    public static RemoteProxyController create(final TransactionalProtocolClient client, final PathAddress pathAddress,
                                               final ProxyOperationAddressTranslator addressTranslator,
                                               final ModelVersion targetKernelVersion) {
        return new RemoteProxyController(client, pathAddress, addressTranslator, targetKernelVersion);
    }

    /**
     * Creates a new remote proxy controller using an existing channel.
     *
     * @param channelAssociation the channel association
     * @param pathAddress the address within the model of the created proxy controller
     * @param addressTranslator the translator to use translating the address for the remote proxy
     * @return the proxy controller
     *
     * @deprecated only present for test case use
     */
    @Deprecated(forRemoval = false)
    public static RemoteProxyController create(final ManagementChannelHandler channelAssociation, final PathAddress pathAddress, final ProxyOperationAddressTranslator addressTranslator) {
        final TransactionalProtocolClient client = TransactionalProtocolHandlers.createClient(channelAssociation);
        // the remote proxy
        return create(client, pathAddress, addressTranslator, ModelVersion.CURRENT);
    }

    /**
     * Get the underlying transactional protocol client.
     *
     * @return the protocol client
     */
    public TransactionalProtocolClient getTransactionalProtocolClient() {
        return client;
    }

    /** {@inheritDoc} */
    @Override
    public PathAddress getProxyNodeAddress() {
        return pathAddress;
    }

    /** {@inheritDoc} */
    @Override
    public void execute(final ModelNode original, final OperationMessageHandler messageHandler, final ProxyOperationControl control,
                        final OperationAttachments attachments, final BlockingTimeout blockingTimeout) {
        // Add blocking support to adhere to the proxy controller API contracts
        final CountDownLatch completed = new CountDownLatch(1);
        final BlockingQueue<TransactionalProtocolClient.PreparedOperation<TransactionalProtocolClient.Operation>> queue = new ArrayBlockingQueue<TransactionalProtocolClient.PreparedOperation<TransactionalProtocolClient.Operation>>(1, true);
        final TransactionalProtocolClient.TransactionalOperationListener<TransactionalProtocolClient.Operation> operationListener = new TransactionalProtocolClient.TransactionalOperationListener<TransactionalProtocolClient.Operation>() {
            @Override
            public void operationPrepared(TransactionalProtocolClient.PreparedOperation<TransactionalProtocolClient.Operation> prepared) {
                if(! queue.offer(prepared)) {
                    prepared.rollback();
                }
            }

            @Override
            public void operationFailed(TransactionalProtocolClient.Operation operation, ModelNode result) {
                try {
                    queue.offer(new BlockingQueueOperationListener.FailedOperation<TransactionalProtocolClient.Operation>(operation, result));
                } finally {
                    // This might not be needed?
                    completed.countDown();
                }
            }

            @Override
            public void operationComplete(TransactionalProtocolClient.Operation operation, OperationResponse response) {
                try {
                    control.operationCompleted(response);
                } finally {
                    // Make sure the handler is called before commit/rollback returns
                    completed.countDown();
                }
            }
        };
        AsyncFuture<OperationResponse> futureResult = null;
        try {
            // Translate the operation
            final PathAddress targetAddress = PathAddress.pathAddress(original.get(OP_ADDR));
            final ModelNode translated = translateOperationForProxy(original, targetAddress);

            // Execute the operation
            ControllerLogger.MGMT_OP_LOGGER.tracef("Executing %s for %s", translated.get(OP).asString(), getProxyNodeAddress());
            futureResult = client.execute(operationListener, translated, messageHandler, attachments);
            // Wait for the prepared response
            final TransactionalProtocolClient.PreparedOperation<TransactionalProtocolClient.Operation> prepared;
            if (blockingTimeout == null) {
                prepared = queue.take();
            } else {
                long timeout = blockingTimeout.getProxyBlockingTimeout(targetAddress, this);
                prepared = queue.poll(timeout, TimeUnit.MILLISECONDS);
                if (prepared == null) {
                    blockingTimeout.proxyTimeoutDetected(targetAddress);
                    futureResult.asyncCancel(true);
                    ModelNode response = getTimeoutResponse(translated.get(OP).asString(), timeout);
                    control.operationFailed(response);
                    ControllerLogger.MGMT_OP_LOGGER.info(response.get(FAILURE_DESCRIPTION).asString());
                    return;
                }
            }
            if(prepared.isFailed()) {
                // If the operation failed, there is nothing more to do
                control.operationFailed(prepared.getPreparedResult());
                return;
            }
            // Send the prepared notification and wrap the OperationTransaction to block on commit/rollback
            final AsyncFuture cancellable = futureResult;
            control.operationPrepared(new ModelController.OperationTransaction() {
                @Override
                public void commit() {
                    prepared.commit();
                    awaitCompletion();
                }

                @Override
                public void rollback() {
                    prepared.rollback();
                    awaitCompletion();
                }

                private void awaitCompletion() {
                    try {
                        // Await the completed notification
                        if (blockingTimeout == null) {
                            completed.await();
                        } else {
                            long timeout = blockingTimeout.getProxyBlockingTimeout(targetAddress, RemoteProxyController.this);
                            if (!completed.await(timeout, TimeUnit.MILLISECONDS)) {
                                cancellable.asyncCancel(true);
                                blockingTimeout.proxyTimeoutDetected(targetAddress);
                                ControllerLogger.MGMT_OP_LOGGER.timeoutAwaitingFinalResponse(translated.get(OP).asString(), getProxyNodeAddress(), timeout);
                            }
                        }
                    } catch (InterruptedException e) {
                        cancellable.asyncCancel(true);
                        ControllerLogger.MGMT_OP_LOGGER.interruptedAwaitingFinalResponse(translated.get(OP).asString(), getProxyNodeAddress());
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }, prepared.getPreparedResult());

        } catch (InterruptedException e) {
            if (futureResult != null) { // it won't be null, as IE can only be thrown after it's assigned
                ControllerLogger.MGMT_OP_LOGGER.interruptedAwaitingInitialResponse(original.get(OP).asString(), getProxyNodeAddress());
                // Cancel the operation
                futureResult.asyncCancel(true);
            }
            control.operationFailed(getCancelledResponse());
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            final ModelNode result = new ModelNode();
            result.get(OUTCOME).set(FAILED);
            result.get(FAILURE_DESCRIPTION).set(e.getLocalizedMessage());
            // Notify the proxy control that the operation failed
            control.operationFailed(result);
        }
    }

    @Override
    public ModelVersion getKernelModelVersion() {
        return targetKernelVersion;
    }

    /**
     * Translate the operation address.
     *
     * @param op the operation
     * @return the new operation
     */
    public ModelNode translateOperationForProxy(final ModelNode op) {
        return translateOperationForProxy(op, PathAddress.pathAddress(op.get(OP_ADDR)));
    }

    private ModelNode translateOperationForProxy(final ModelNode op, PathAddress targetAddress) {
        final PathAddress translated = addressTranslator.translateAddress(targetAddress);
        if (targetAddress.equals(translated)) {
            return op;
        }
        final ModelNode proxyOp = op.clone();
        proxyOp.get(OP_ADDR).set(translated.toModelNode());
        return proxyOp;

    }

    private static ModelNode getCancelledResponse() {
        ModelNode result = new ModelNode();
        result.get(OUTCOME).set(CANCELLED);
        result.get(FAILURE_DESCRIPTION).set(ControllerLogger.ROOT_LOGGER.operationCancelled());
        return result;
    }

    private ModelNode getTimeoutResponse(String operation, long timeout) {
        ModelNode response = new ModelNode();
        response.get(OUTCOME).set(FAILED);
        response.get(FAILURE_DESCRIPTION).set(ControllerLogger.ROOT_LOGGER.proxiedOperationTimedOut(operation, pathAddress, timeout));
        return response;
    }
}
