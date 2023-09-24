/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.remote;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_MECHANISM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CALLER_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXECUTE_FOR_COORDINATOR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RELOAD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SHUTDOWN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYNC_REMOVED_FOR_READD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USER;
import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;
import static org.jboss.as.controller.logging.ControllerLogger.ROOT_LOGGER;

import java.io.DataInput;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import org.jboss.as.controller.AccessAuditContext;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.impl.ModelControllerProtocol;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.core.security.AccessMechanism;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.protocol.mgmt.ActiveOperation;
import org.jboss.as.protocol.mgmt.FlushableDataOutput;
import org.jboss.as.protocol.mgmt.ManagementChannelAssociation;
import org.jboss.as.protocol.mgmt.ManagementProtocol;
import org.jboss.as.protocol.mgmt.ManagementRequestContext;
import org.jboss.as.protocol.mgmt.ManagementRequestHandler;
import org.jboss.as.protocol.mgmt.ManagementRequestHandlerFactory;
import org.jboss.as.protocol.mgmt.ManagementRequestHeader;
import org.jboss.as.protocol.mgmt.ManagementResponseHeader;
import org.jboss.as.protocol.mgmt.ProtocolUtils;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * Operation handlers for the remote implementation of {@link org.jboss.as.controller.client.ModelControllerClient}
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Emanuel Muckenhuber
 */
public class ModelControllerClientOperationHandler implements ManagementRequestHandlerFactory {

    private static final Set<String> PREPARED_RESPONSE_OPERATIONS = Set.of(RELOAD, SHUTDOWN);
    private final ModelController controller;

    private final ManagementChannelAssociation channelAssociation;
    private final Executor clientRequestExecutor;
    private final SecurityIdentity connectionIdentity;
    private final ResponseAttachmentInputStreamSupport responseAttachmentSupport;

    public ModelControllerClientOperationHandler(final ModelController controller,
                                                 final ManagementChannelAssociation channelAssociation,
                                                 final ResponseAttachmentInputStreamSupport responseAttachmentSupport,
                                                 final ExecutorService clientRequestExecutor) {
        this(controller, channelAssociation, responseAttachmentSupport, clientRequestExecutor, null);
    }

    public ModelControllerClientOperationHandler(final ModelController controller,
                                                 final ManagementChannelAssociation channelAssociation,
                                                 final ResponseAttachmentInputStreamSupport responseAttachmentSupport,
                                                 final ExecutorService clientRequestExecutor,
                                                 final SecurityIdentity connectionIdentity) {
        this.controller = controller;
        this.channelAssociation = channelAssociation;
        this.responseAttachmentSupport = responseAttachmentSupport;
        this.connectionIdentity = connectionIdentity;
        this.clientRequestExecutor = clientRequestExecutor;
    }

    @Override
    public ManagementRequestHandler<?, ?> resolveHandler(final RequestHandlerChain handlers, final ManagementRequestHeader header) {
        switch (header.getOperationId()) {
            case ModelControllerProtocol.EXECUTE_ASYNC_CLIENT_REQUEST:
            case ModelControllerProtocol.EXECUTE_CLIENT_REQUEST:
                // initialize the operation ctx before executing the request handler
                handlers.registerActiveOperation(header.getBatchId(), null);
                return new ExecuteRequestHandler();
            case ModelControllerProtocol.CANCEL_ASYNC_REQUEST:
                return new CancelAsyncRequestHandler();
            case ModelControllerProtocol.GET_CHUNKED_INPUTSTREAM_REQUEST:
                // initialize the operation ctx before executing the request handler
                handlers.registerActiveOperation(header.getBatchId(), null);
                return responseAttachmentSupport.getReadHandler();
            case ModelControllerProtocol.CLOSE_INPUTSTREAM_REQUEST:
                // initialize the operation ctx before executing the request handler
                handlers.registerActiveOperation(header.getBatchId(), null);
                return responseAttachmentSupport.getCloseHandler();
        }
        return handlers.resolveNext();
    }

    class ExecuteRequestHandler implements ManagementRequestHandler<ModelNode, Void> {
        @Override
        public void handleRequest(final DataInput input, final ActiveOperation.ResultHandler<ModelNode> resultHandler,
                                  final ManagementRequestContext<Void> context) throws IOException {
            ControllerLogger.MGMT_OP_LOGGER.tracef("Handling ExecuteRequest for %d", context.getOperationId());
            InetSocketAddress peerSocketAddress = channelAssociation.getChannel().getConnection().getPeerAddress(InetSocketAddress.class);
            final InetAddress remoteAddress = peerSocketAddress != null ? peerSocketAddress.getAddress() : null;

            final ModelNode operation = new ModelNode();
            ProtocolUtils.expectHeader(input, ModelControllerProtocol.PARAM_OPERATION);
            operation.readExternal(input);

            ProtocolUtils.expectHeader(input, ModelControllerProtocol.PARAM_INPUTSTREAMS_LENGTH);
            final int attachmentsLength = input.readInt();
            context.executeAsync(new ManagementRequestContext.AsyncTask<Void>() {
                @Override
                public void execute(final ManagementRequestContext<Void> context) throws Exception {
                    final ManagementResponseHeader response = ManagementResponseHeader.create(context.getRequestHeader());

                    try {
                        AccessAuditContext.doAs(connectionIdentity, remoteAddress, new PrivilegedExceptionAction<Void>() {
                            @Override
                            public Void run() throws Exception {
                                final CompletedCallback callback = new CompletedCallback(response, context, resultHandler);
                                doExecute(operation, attachmentsLength, context, callback);
                                return null;
                            }
                        });
                    } catch (PrivilegedActionException e) {
                        throw e.getException();
                    }
                }
            }, clientRequestExecutor);
        }

        private void doExecute(final ModelNode operation, final int attachmentsLength,
                               final ManagementRequestContext<Void> context, final CompletedCallback callback) {

            ControllerLogger.MGMT_OP_LOGGER.tracef("Executing ExecuteRequest for %d", context.getOperationId());
            // Header manipulation
            final ModelNode headers = operation.get(OPERATION_HEADERS);
            //Add a header to show that this operation comes from a user. If this is a host controller and the operation needs propagating to the
            //servers it will be removed by the domain ops responsible for propagation to the servers.
            //If more headers are removed here, they must also be removed from the http interface (DomainApiHandler)
            headers.get(CALLER_TYPE).set(USER);
            headers.get(ACCESS_MECHANISM).set(AccessMechanism.NATIVE.toString());
            // Don't allow a domain-uuid operation header from a user call
            if (headers.hasDefined(DOMAIN_UUID)) {
                headers.remove(DOMAIN_UUID);
            }
            // Don't allow a execute-for-coordinator operation header from a user call
            if (headers.hasDefined(EXECUTE_FOR_COORDINATOR)) {
                headers.remove(EXECUTE_FOR_COORDINATOR);
            }
            // Only used internally on a slave when syncing the model
            if (headers.hasDefined(SYNC_REMOVED_FOR_READD)) {
                headers.remove(SYNC_REMOVED_FOR_READD);
            }

            final ManagementRequestHeader header = ManagementRequestHeader.class.cast(context.getRequestHeader());
            final int batchId = header.getBatchId();
            final ModelNode result = new ModelNode();

            // Send the prepared response for :reload operations
            final boolean sendPreparedOperation = sendPreparedResponse(operation);
            final ModelController.OperationTransactionControl transactionControl = sendPreparedOperation ? new ModelController.OperationTransactionControl() {

                @Override
                public void operationPrepared(ModelController.OperationTransaction transaction, ModelNode preparedResult) {
                    // Shouldn't be called but if it is, send the result immediately
                    operationPrepared(transaction, preparedResult, null);
                }

                @Override
                public void operationPrepared(ModelController.OperationTransaction transaction, final ModelNode preparedResult, OperationContext context) {
                    transaction.commit();
                    if (context == null || !RELOAD.equals(operation.get(OP).asString())) { // TODO deal with shutdown as well,
                                                                                           // the handlers for which have some
                                                                                           // subtleties that need thought
                        sendResponse(preparedResult);
                    } else {
                        context.attach(EarlyResponseSendListener.ATTACHMENT_KEY, new EarlyResponseSendListener() {
                            @Override
                            public void sendEarlyResponse(OperationContext.ResultAction resultAction) {
                                sendResponse(preparedResult);
                            }
                        });
                    }
                }

                private void sendResponse(ModelNode preparedResult) {
                    // Fix prepared result
                    preparedResult.get(OUTCOME).set(SUCCESS);
                    preparedResult.get(RESULT);
                    callback.sendResponse(preparedResult);
                }
            } : ModelController.OperationTransactionControl.COMMIT;

            final OperationMessageHandler messageHandlerProxy = OperationMessageHandler.DISCARD;
            final OperationAttachmentsProxy attachmentsProxy = OperationAttachmentsProxy.create(operation, channelAssociation, batchId, attachmentsLength);
            try {
                ROOT_LOGGER.tracef("Executing client request %d(%d)", batchId, header.getRequestId());
                OperationResponse response = controller.execute(attachmentsProxy, messageHandlerProxy, transactionControl);

                responseAttachmentSupport.registerStreams(context.getOperationId(), response.getInputStreams());

                result.set(response.getResponseNode());
            } catch (Throwable t) {
                final ModelNode failure = new ModelNode();
                failure.get(OUTCOME).set(FAILED);
                failure.get(FAILURE_DESCRIPTION).set(t.getClass().getName() + ":" + t.getMessage());
                result.set(failure);
                attachmentsProxy.shutdown();
                ControllerLogger.MGMT_OP_LOGGER.unexpectedOperationExecutionException(t, Collections.singletonList(operation));
            } finally {
                ROOT_LOGGER.tracef("Executed client request %d", batchId);
            }
            // Send the result
            callback.sendResponse(result);
        }

    }

    /**
     * Determine whether the prepared response should be sent, before the operation completed. This is needed in order
     * that operations like :reload() can be executed without causing communication failures.
     *
     * @param operation the operation to be executed
     * @return {@code true} if the prepared result should be sent, {@code false} otherwise
     */
    private boolean sendPreparedResponse(final ModelNode operation) {
        try {
            final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
            final String op = operation.get(OP).asString();
            final int size = address.size();
            if (size == 0) {
                if (PREPARED_RESPONSE_OPERATIONS.contains(op)) {
                    return true;
                } else if (COMPOSITE.equals(op)) {
                    // TODO
                    return false;
                } else {
                    return false;
                }
            } else if (size == 1) {
                if (HOST.equals(address.getLastElement().getKey())) {
                    return PREPARED_RESPONSE_OPERATIONS.contains(op);
                }
            }
            return false;
        } catch(Exception ex) {
            return false;
        }
    }

    private static class CompletedCallback {

        private volatile boolean completed;
        private final ManagementResponseHeader response;
        private final ManagementRequestContext<Void> responseContext;
        private final ActiveOperation.ResultHandler<ModelNode> resultHandler;

        private CompletedCallback(final ManagementResponseHeader response,
                                  final ManagementRequestContext<Void> responseContext,
                                  final ActiveOperation.ResultHandler<ModelNode> resultHandler) {
            this.response = response;
            this.responseContext = responseContext;
            this.resultHandler = resultHandler;
        }

        synchronized void sendResponse(final ModelNode result) {
            // only send a single response
            if (completed) {
                return;
            }
            completed = true;
            // WFLY-3090 Protect the communication channel from getting closed due to administrative
            // cancellation of the management op by using a separate thread to send
            final CountDownLatch latch = new CountDownLatch(1);
            final IOExceptionHolder exceptionHolder = new IOExceptionHolder();

            boolean accepted = responseContext.executeAsync(new ManagementRequestContext.AsyncTask<Void>() {

                @Override
                public void execute(final ManagementRequestContext<Void> context) throws Exception {

                    FlushableDataOutput output = null;
                    try {
                        MGMT_OP_LOGGER.tracef("Transmitting response for %d", context.getOperationId());
                        output = responseContext.writeMessage(response);
                        output.write(ModelControllerProtocol.PARAM_RESPONSE);
                        result.writeExternal(output);
                        output.writeByte(ManagementProtocol.RESPONSE_END);
                        output.close();
                    } catch (IOException e) {
                        exceptionHolder.exception = e;
                    } finally {
                        StreamUtils.safeClose(output);
                        latch.countDown();
                    }
                }
            }, false);

            if (accepted) {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (exceptionHolder.exception != null) {
                    resultHandler.failed(exceptionHolder.exception);
                } else {
                    resultHandler.done(result);
                }
            }
        }

    }

    private static class CancelAsyncRequestHandler implements ManagementRequestHandler<ModelNode, Void> {

        @Override
        public void handleRequest(final DataInput input, final ActiveOperation.ResultHandler<ModelNode> resultHandler, final ManagementRequestContext<Void> context) throws IOException {
            ControllerLogger.MGMT_OP_LOGGER.tracef("Cancellation of %d requested", context.getOperationId());
            context.executeAsync(new ManagementRequestContext.AsyncTask<Void>() {
                @Override
                public void execute(ManagementRequestContext<Void> context) throws Exception {
                    final ManagementResponseHeader response = ManagementResponseHeader.create(context.getRequestHeader());
                    final FlushableDataOutput output = context.writeMessage(response);
                    try {
                        output.writeByte(ManagementProtocol.RESPONSE_END);
                        output.close();
                    } finally {
                        StreamUtils.safeClose(output);
                    }
                }
            }, false);
            resultHandler.cancel();
        }
    }

    private static class IOExceptionHolder {
        private IOException exception;
    }

}
