/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.remote.TransactionalProtocolClient;
import org.jboss.as.controller.remote.TransactionalProtocolHandlers;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.server.operations.ServerProcessStateHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.threads.AsyncFuture;

/**
 * A proxy dispatching operations to the managed server.
 *
 * @author Emanuel Muckenhuber
 */
class ManagedServerProxy implements TransactionalProtocolClient {

    private static final TransactionalProtocolClient DISCONNECTED = new DisconnectedProtocolClient();

    private final ManagedServer server;
    private final Map<TransactionalProtocolClient, Set<AsyncFuture<OperationResponse>>> activeRequests = new HashMap<>();
    private volatile TransactionalProtocolClient remoteClient;

    ManagedServerProxy(final ManagedServer server) {
        this.server = server;
        this.remoteClient = DISCONNECTED;
    }

    synchronized void connected(final TransactionalProtocolClient remoteClient) {
        this.remoteClient = remoteClient;
    }

    synchronized boolean disconnected(final TransactionalProtocolClient old) {
        if(remoteClient == old) {
            remoteClient = DISCONNECTED;

            // Cancel any inflight requests from the old TransactionalProtocolClient
            Set<AsyncFuture<OperationResponse>> inFlight = activeRequests.remove(old);
            if (inFlight != null) {
                for (AsyncFuture<OperationResponse> future : inFlight) {
                    future.asyncCancel(true);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public AsyncFuture<OperationResponse> execute(final TransactionalOperationListener<Operation> listener, final ModelNode operation, final OperationMessageHandler messageHandler, final OperationAttachments attachments) throws IOException {
        return execute(listener, TransactionalProtocolHandlers.wrap(operation, messageHandler, attachments));
    }

    @Override
    public <T extends Operation> AsyncFuture<OperationResponse> execute(final TransactionalOperationListener<T> listener, final T operation) throws IOException {
        final TransactionalProtocolClient remoteClient = this.remoteClient;
        final ModelNode op = operation.getOperation();

        if (remoteClient == DISCONNECTED) {
            // Handle the restartRequired operation also when disconnected
            final String operationName = op.get(OP).asString();
            if (ServerProcessStateHandler.REQUIRE_RESTART_OPERATION.equals(operationName)) {
                server.requireReload();
            }
        }
        AsyncFuture<OperationResponse> future = remoteClient.execute(listener, operation);
        registerFuture(remoteClient, future);
        return future;
    }

    private synchronized void registerFuture(TransactionalProtocolClient remoteClient, AsyncFuture<OperationResponse> future) {
        if (this.remoteClient != remoteClient) {
            // We were disconnected. Just cancel this future
            future.asyncCancel(true);
        } else {
            // Track the future for cancellation on disconnect
            Set<AsyncFuture<OperationResponse>> futures = activeRequests.get(remoteClient);
            if (futures == null) {
                futures = new HashSet<>();
                activeRequests.put(remoteClient, futures);
            }
            futures.add(future);

            // Make sure we clean up
            // ignore the 1st parameter of handle* callbacks, that's the _underlying_ future,
            // not the one just added to the "futures" set
            future.addListener(new AsyncFuture.Listener<OperationResponse, TransactionalProtocolClient>() {
                @Override
                public void handleComplete(AsyncFuture<? extends OperationResponse> ignored, TransactionalProtocolClient attachment) {
                    futureDone(attachment, future);
                }

                @Override
                public void handleFailed(AsyncFuture<? extends OperationResponse> ignored, Throwable cause, TransactionalProtocolClient attachment) {
                    futureDone(attachment, future);
                }

                @Override
                public void handleCancelled(AsyncFuture<? extends OperationResponse> ignored, TransactionalProtocolClient attachment) {
                    futureDone(attachment, future);
                }
            }, remoteClient);
        }
    }

    private synchronized void futureDone(TransactionalProtocolClient remoteClient, AsyncFuture<? extends OperationResponse> future) {
        Set<AsyncFuture<OperationResponse>> futures = activeRequests.get(remoteClient);
        if (futures != null) {
            //noinspection SuspiciousMethodCalls
            futures.remove(future);
        }
    }


    static final class DisconnectedProtocolClient implements TransactionalProtocolClient {

        @Override
        public AsyncFuture<OperationResponse> execute(TransactionalOperationListener<Operation> listener, ModelNode operation, OperationMessageHandler messageHandler, OperationAttachments attachments) throws IOException {
            return execute(listener, TransactionalProtocolHandlers.wrap(operation, messageHandler, attachments));
        }

        @Override
        public <T extends Operation> AsyncFuture<OperationResponse> execute(TransactionalOperationListener<T> listener, T operation) throws IOException {
            throw HostControllerLogger.ROOT_LOGGER.channelClosed();
        }

    }

}
