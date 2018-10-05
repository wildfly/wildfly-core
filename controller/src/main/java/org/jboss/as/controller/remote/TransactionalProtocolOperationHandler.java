/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller.remote;

import static java.security.AccessController.doPrivileged;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CANCELLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;
import static org.jboss.as.controller.logging.ControllerLogger.ROOT_LOGGER;
import static org.jboss.as.controller.remote.IdentityAddressProtocolUtil.read;

import java.io.DataInput;
import java.io.IOException;
import java.net.InetAddress;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import org.jboss.as.controller.AccessAuditContext;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.access.InVmAccess;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.impl.ModelControllerProtocol;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.remote.IdentityAddressProtocolUtil.PropagatedIdentity;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.protocol.mgmt.ActiveOperation;
import org.jboss.as.protocol.mgmt.FlushableDataOutput;
import org.jboss.as.protocol.mgmt.ManagementChannelAssociation;
import org.jboss.as.protocol.mgmt.ManagementProtocol;
import org.jboss.as.protocol.mgmt.ManagementProtocolHeader;
import org.jboss.as.protocol.mgmt.ManagementRequestContext;
import org.jboss.as.protocol.mgmt.ManagementRequestContext.AsyncTask;
import org.jboss.as.protocol.mgmt.ManagementRequestHandler;
import org.jboss.as.protocol.mgmt.ManagementRequestHandlerFactory;
import org.jboss.as.protocol.mgmt.ManagementRequestHeader;
import org.jboss.as.protocol.mgmt.ManagementResponseHeader;
import org.jboss.as.protocol.mgmt.ProtocolUtils;
import org.jboss.dmr.ModelNode;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * The transactional request handler for a remote {@link TransactionalProtocolClient}.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class TransactionalProtocolOperationHandler implements ManagementRequestHandlerFactory {

    private final ModelController controller;
    private final ManagementChannelAssociation channelAssociation;
    private final ResponseAttachmentInputStreamSupport responseAttachmentSupport;

    public TransactionalProtocolOperationHandler(final ModelController controller, final ManagementChannelAssociation channelAssociation,
                                                 final ResponseAttachmentInputStreamSupport responseAttachmentSupport) {
        this.controller = controller;
        this.channelAssociation = channelAssociation;
        this.responseAttachmentSupport = responseAttachmentSupport;
    }

    @Override
    public ManagementRequestHandler<?, ?> resolveHandler(RequestHandlerChain handlers, ManagementRequestHeader request) {
        switch(request.getOperationId()) {
            case ModelControllerProtocol.EXECUTE_TX_REQUEST: {
                // Initialize the request context
                final ExecuteRequestContext executeRequestContext = new ExecuteRequestContext(responseAttachmentSupport);
                try {
                    executeRequestContext.operation = handlers.registerActiveOperation(request.getBatchId(), executeRequestContext, executeRequestContext);
                } catch (IllegalStateException ise) {
                    // WFLY-3381 Unusual case where the initial request lost a race with a COMPLETE_TX_REQUEST carrying a cancellation
                    return new AbortOperationHandler(true);
                }
                return new ExecuteRequestHandler();
            }
            case ModelControllerProtocol.COMPLETE_TX_REQUEST: {
                final ExecuteRequestContext executeRequestContext = new ExecuteRequestContext(responseAttachmentSupport);
                try {
                    executeRequestContext.operation = handlers.registerActiveOperation(request.getBatchId(), executeRequestContext, executeRequestContext);
                    // WLFY-3381 Unusual case where the initial request must have lost a race with a COMPLETE_TX_REQUEST carrying a cancellation
                    return new AbortOperationHandler(false);
                } catch (IllegalStateException ise) {
                    // Expected case -- not a normal commit/rollback or one where a COMPLETE_TX_REQUEST with a cancel
                    // won a race with the initial request
                }
                return new CompleteTxOperationHandler();
            }
            case ModelControllerProtocol.GET_CHUNKED_INPUTSTREAM_REQUEST: {
                // initialize the operation ctx before executing the request handler
                handlers.registerActiveOperation(request.getBatchId(), null);
                return responseAttachmentSupport.getReadHandler();
            }
            case ModelControllerProtocol.CLOSE_INPUTSTREAM_REQUEST: {
                // initialize the operation ctx before executing the request handler
                handlers.registerActiveOperation(request.getBatchId(), null);
                return responseAttachmentSupport.getCloseHandler();
            }
        }
        return handlers.resolveNext();
    }

    /**
     * The request handler for requests from {@link org.jboss.as.controller.remote.TransactionalProtocolClient#execute}.
     */
    private class ExecuteRequestHandler implements ManagementRequestHandler<Void, ExecuteRequestContext> {

        @Override
        public void handleRequest(final DataInput input, final ActiveOperation.ResultHandler<Void> resultHandler, final ManagementRequestContext<ExecuteRequestContext> context) throws IOException {
            ControllerLogger.MGMT_OP_LOGGER.tracef("Handling transactional ExecuteRequest for %d", context.getOperationId());

            final ExecutableRequest executableRequest = ExecutableRequest.parse(input, channelAssociation);
            PropagatedIdentity propagatedIdentity = executableRequest.propagatedIdentity;
            final SecurityIdentity securityIdentity = propagatedIdentity != null ? propagatedIdentity.securityIdentity : null;
            final InetAddress remoteAddress = propagatedIdentity != null ? propagatedIdentity.inetAddress : null;

            final PrivilegedAction<Void> action = new PrivilegedAction<Void>() {

                @Override
                public Void run() {
                    doExecute(executableRequest.operation, executableRequest.attachmentsLength, context);
                    return null;
                }
            };

            // Set the response information and execute the operation
            final ExecuteRequestContext executeRequestContext = context.getAttachment();
            executeRequestContext.initialize(context);

            @SuppressWarnings("deprecation")
            AsyncTask<TransactionalProtocolOperationHandler.ExecuteRequestContext> task =
                    new ManagementRequestContext.MultipleResponseAsyncTask<TransactionalProtocolOperationHandler.ExecuteRequestContext>() {

                @Override
                public void execute(final ManagementRequestContext<ExecuteRequestContext> context) throws Exception {
                    final Supplier<Void> execution = new Supplier<Void>() {
                        @Override
                        public Void get() {
                            if (executableRequest.inVmCall && securityIdentity == null) {
                                return InVmAccess.runInVm((PrivilegedAction<Void>) () -> {
                                    AccessAuditContext.doAs(false, null, remoteAddress, action);
                                    return null;
                                });
                            } else {
                                AccessAuditContext.doAs(securityIdentity != null, securityIdentity, remoteAddress, action);
                                return null;
                            }
                        }
                    };
                    privilegedExecution().execute(execution);
                }

                @Override
                public ManagementProtocolHeader getCurrentRequestHeader() {
                    ManagementRequestContext current = executeRequestContext.responseChannel;
                    return current == null ? null : current.getRequestHeader();
                }
            };
            context.executeAsync(task);
        }

        protected void doExecute(final ModelNode operation, final int attachmentsLength, final ManagementRequestContext<ExecuteRequestContext> context) {
            ControllerLogger.MGMT_OP_LOGGER.tracef("Executing transactional ExecuteRequest for %d", context.getOperationId());
            final ExecuteRequestContext executeRequestContext = context.getAttachment();
            // Set the response information
            executeRequestContext.initialize(context);
            final Integer batchId = executeRequestContext.getOperationId();
            final OperationMessageHandlerProxy messageHandlerProxy = new OperationMessageHandlerProxy(channelAssociation, batchId);
            final ProxyOperationTransactionControl control = new ProxyOperationTransactionControl(executeRequestContext);
            final OperationAttachmentsProxy attachmentsProxy = OperationAttachmentsProxy.create(operation, channelAssociation, batchId, attachmentsLength);
            final OperationResponse result;
            try {
                // Execute the operation
                result = internalExecute(attachmentsProxy, context, messageHandlerProxy, control);
            } catch (Throwable t) {

                final ModelNode failure = new ModelNode();
                failure.get(OUTCOME).set(FAILED);
                failure.get(FAILURE_DESCRIPTION).set(t.getClass().getName() + ":" + t.getMessage());
                executeRequestContext.failed(failure);
                attachmentsProxy.shutdown();
                ControllerLogger.MGMT_OP_LOGGER.unexpectedOperationExecutionException(t, Collections.singletonList(operation));
                return;
            }

            // At this point the transactional request either failed prior to preparing the transaction,
            // or it has completed.
            if (!executeRequestContext.prepared) {
                // If internalExecute did not result in a prepare, it failed
                executeRequestContext.failed(result.getResponseNode());
            } else {
                executeRequestContext.completed(result);
            }
        }
    }

    private static class ExecutableRequest {
        private final ModelNode operation;
        private final int attachmentsLength;
        private final PropagatedIdentity propagatedIdentity;
        private final boolean inVmCall;

        private ExecutableRequest(ModelNode operation, int attachmentsLength, PropagatedIdentity propagatedIdentity, boolean inVmCall) {
            this.operation = operation;
            this.attachmentsLength = attachmentsLength;
            this.propagatedIdentity = propagatedIdentity;
            this.inVmCall = inVmCall;
        }

        static ExecutableRequest parse(DataInput input, ManagementChannelAssociation channelAssociation) throws IOException {
            final ModelNode operation = new ModelNode();
            ProtocolUtils.expectHeader(input, ModelControllerProtocol.PARAM_OPERATION);
            operation.readExternal(input);
            ProtocolUtils.expectHeader(input, ModelControllerProtocol.PARAM_INPUTSTREAMS_LENGTH);
            final int attachmentsLength = input.readInt();

            final PropagatedIdentity propagatedIdentity;
            final Boolean readIdentity = channelAssociation.getAttachments().getAttachment(TransactionalProtocolClient.SEND_IDENTITY);
            propagatedIdentity = (readIdentity != null && readIdentity) ? read(input) : null;

            final Boolean readSendInVm = channelAssociation.getAttachments().getAttachment(TransactionalProtocolClient.SEND_IN_VM);
            boolean inVmCall = false;
            if (readSendInVm != null && readSendInVm) {
                ProtocolUtils.expectHeader(input, ModelControllerProtocol.PARAM_IN_VM_CALL);
                inVmCall = input.readBoolean();
            }

            return new ExecutableRequest(operation, attachmentsLength, propagatedIdentity, inVmCall);
        }
    }

    /**
     * Subclasses can override this method to determine how to execute the method, e.g. attach to an existing operation or not
     *
     * @param operation the operation being executed
     * @param messageHandler the operation message handler proxy
     * @param control the operation transaction control
     * @return the result of the executed operation
     */
    protected OperationResponse internalExecute(final Operation operation, final ManagementRequestContext<?> context, final OperationMessageHandler messageHandler, final ModelController.OperationTransactionControl control) {
        // Execute the operation
        return controller.execute(
                operation,
                messageHandler,
                control);
    }

    /**
     * The request handler for requests from {@link org.jboss.as.controller.remote.TransactionalProtocolClientImpl.CompleteTxRequest}
     */
    private class CompleteTxOperationHandler implements ManagementRequestHandler<Void, ExecuteRequestContext> {

        @Override
        public void handleRequest(final DataInput input, final ActiveOperation.ResultHandler<Void> resultHandler, final ManagementRequestContext<ExecuteRequestContext> context) throws IOException {
            final ExecuteRequestContext executeRequestContext = context.getAttachment();
            final byte commitOrRollback = input.readByte();

            // Complete transaction, either commit or rollback
            executeRequestContext.completeTx(context, commitOrRollback == ModelControllerProtocol.PARAM_COMMIT);
        }

    }

    private class AbortOperationHandler implements ManagementRequestHandler<Void, ExecuteRequestContext> {

        private final boolean forExecuteTxRequest;

        private AbortOperationHandler(boolean forExecuteTxRequest) {
            this.forExecuteTxRequest = forExecuteTxRequest;
        }

        @Override
        public void handleRequest(final DataInput input, final ActiveOperation.ResultHandler<Void> resultHandler, final ManagementRequestContext<ExecuteRequestContext> context) throws IOException {

            if (forExecuteTxRequest) {
                try {
                    // Read and discard the input
                    ExecutableRequest.parse(input, channelAssociation);
                } finally {
                    ControllerLogger.MGMT_OP_LOGGER.tracef("aborting (cancel received before request) for %d", context.getOperationId());
                    ModelNode response = new ModelNode();
                    response.get(OUTCOME).set(CANCELLED);
                    context.getAttachment().initialize(context);
                    context.getAttachment().failed(response);
                }
            } else {
                // This was a COMPLETE_TX_REQUEST that came in before the original EXECUTE_TX_REQUEST
                final byte commitOrRollback = input.readByte();
                if (commitOrRollback == ModelControllerProtocol.PARAM_COMMIT) {
                    // a cancel would not use PARAM_COMMIT; this was a request that didn't match any existing op
                    // Likely the request was cancelled and removed but the commit message was in process
                    throw ControllerLogger.MGMT_OP_LOGGER.responseHandlerNotFound(context.getOperationId());
                }
                // else this was a cancel request. Do nothing and wait for the initial operation request to come in
                // and see the pre-existing ActiveOperation and then call this with forExecuteTxRequest=true
            }
        }

    }

    /**
     * OperationTransactionControl that handle operationPrepared by signalling the ExecutionRequestContext
     * associated with the active operation.
     */
    private static class ProxyOperationTransactionControl implements ModelController.OperationTransactionControl {

        private final ExecuteRequestContext requestContext;

        ProxyOperationTransactionControl(ExecuteRequestContext requestContext) {
            this.requestContext = requestContext;
        }

        @Override
        public void operationPrepared(final ModelController.OperationTransaction transaction, final ModelNode result) {

            requestContext.prepare(transaction, result);
            try {
                // Wait for the commit or rollback message
                requestContext.txCompletedLatch.await();
            } catch (InterruptedException e) {
                // requestContext.getResultHandler().failed(e);
                ROOT_LOGGER.tracef("Clearing interrupted status from client request %d", requestContext.getOperationId());
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Attachment to a ManagementRequestContext that handlers dealing with the various requests
     * associated with a single overall operation can use to coordinate execution.
     */
    private static class ExecuteRequestContext implements ActiveOperation.CompletedCallback<Void> {

        /** The operation being executed */
        private ActiveOperation<Void, ExecuteRequestContext> operation;
        /**
         * Whether the prepare method has been invoked. Once the initial response to the remote side goes out,
         * then {@code responseChannel} will be set to null
         */
        private boolean prepared;
        /**
         * True if we get a compleTx call before the prepare method is invoked.
         * This would mean the op was cancelled on the remote side.
         */
        private boolean rollbackOnPrepare;
        /**
         * The tx provided to prepare.
         */
        private ModelController.OperationTransaction activeTx;
        /**
         * Not null if we owe the remote side a response; null if the currently expected response has gone out.
         * Use this to track whether a response has been sent to the remote side.
         */
        private ManagementRequestContext<ExecuteRequestContext> responseChannel;
        /**
         * The thread that calls the prepare method will block on this so it eventually must be tripped once prepare is called
         */
        private final CountDownLatch txCompletedLatch = new CountDownLatch(1);
        /**
         * Set to true once the prepare method has been invoked and a completeTx call is received.
         * Used to detect a second completeTx call coming in to cancel the op the final response goes out.
         */
        private boolean txCompleted;
        /**
         * A final response to the op that was passed to complete after the op had sent a prepare message
         * to the remote side but before any completeTx message was received. This indicates a failure or
         * local cancellation while waiting for completeTx. The final response is cached in this field
         * so it can be sent to the client once the completeTx call comes in. Until that happens there
         * is no responseChannel available to send the response, as the prepare message has completed the
         * request/response pair from the initial request
         */
        private OperationResponse postPrepareRaceResponse;

        /** Support object for managing any streams associated with the response */
        final ResponseAttachmentInputStreamSupport streamSupport;

        ExecuteRequestContext(final ResponseAttachmentInputStreamSupport streamSupport) {
            this.streamSupport = streamSupport;
        }

        Integer getOperationId() {
            return operation.getOperationId();
        }

        ActiveOperation.ResultHandler<Void> getResultHandler() {
            return operation.getResultHandler();
        }

        @Override
        public void completed(Void result) {
            //
        }

        @Override
        public synchronized void failed(Exception e) {
            if(prepared) {
                final ModelController.OperationTransaction transaction = activeTx;
                activeTx = null;
                if(transaction != null) {
                    try {
                        transaction.rollback();
                    } finally {
                        txCompletedLatch.countDown();
                    }
                }
            } else if (responseChannel != null) {
                rollbackOnPrepare = true;
                // Failed in a step before prepare, send error response
                final String message = e.getMessage() != null ? e.getMessage() : "failure before rollback " + e.getClass().getName();
                final ModelNode response = new ModelNode();
                response.get(OUTCOME).set(FAILED);
                response.get(FAILURE_DESCRIPTION).set(message);
                ControllerLogger.MGMT_OP_LOGGER.tracef("sending pre-prepare failed response for %d  --- interrupted: %s", getOperationId(), (Object) Thread.currentThread().isInterrupted());
                try {
                    sendResponse(responseChannel, ModelControllerProtocol.PARAM_OPERATION_FAILED, response);
                    responseChannel = null;
                } catch (IOException ignored) {
                    ControllerLogger.MGMT_OP_LOGGER.failedSendingFailedResponse(ignored, response, getOperationId());
                }
            }
        }

        @Override
        public void cancelled() {
            //
        }

        /**
         * Initializes this object with a reference to the {@code ManagementRequestContext} it can
         * use for executing asynchronous tasks and sending messages to the remote client.
         * @param context the ManagementRequestContext
         */
        synchronized void initialize(final ManagementRequestContext<ExecuteRequestContext> context) {
            assert ! prepared;
            assert activeTx == null;
            this.responseChannel = context;
            ControllerLogger.MGMT_OP_LOGGER.tracef("Initialized for %d", getOperationId());

        }

        /** Signal from ProxyOperationTransactionControl that the operation is prepared */
        synchronized void prepare(final ModelController.OperationTransaction tx, final ModelNode result) {
            assert !prepared;
            prepared = true;
            if(rollbackOnPrepare) {
                try {
                    tx.rollback();
                    ControllerLogger.MGMT_OP_LOGGER.tracef("rolled back on prepare for %d  --- interrupted: %s", getOperationId(), (Object) Thread.currentThread().isInterrupted());
                } finally {
                    txCompletedLatch.countDown();
                }

                // no response to remote side here; the response will go out when this thread executing
                // the now rolled-back op returns in ExecuteRequestHandler.doExecute

            } else {
                assert activeTx == null;
                assert responseChannel != null;
                activeTx = tx;
                ControllerLogger.MGMT_OP_LOGGER.tracef("sending prepared response for %d  --- interrupted: %s", getOperationId(), (Object) Thread.currentThread().isInterrupted());
                try {
                    sendResponse(responseChannel, ModelControllerProtocol.PARAM_OPERATION_PREPARED, result);
                    responseChannel = null; // we've now sent a response to the original request, so we can't use this one further
                } catch (IOException e) {
                    getResultHandler().failed(e); // this will eventually call back into failed(e) above and roll back the tx
                }
            }
        }

        /** Invoked when we receive a ModelControllerProtocol.COMPLETE_TX_REQUEST request, either a tx commit/rollback or a cancel */
        synchronized void completeTx(final ManagementRequestContext<ExecuteRequestContext> context, final boolean commit) {
            if (!prepared) {
                assert !commit; // can only be cancel before it's prepared

                ControllerLogger.MGMT_OP_LOGGER.tracef("completeTx (cancel unprepared) for %d", getOperationId());
                rollbackOnPrepare = true;
                cancel(context);

                // response is sent when the cancalled op results in the thead returning in ExecuteRequestHandler.doExecute

            } else if (txCompleted) {
                // A 2nd call means a cancellation from the remote side after the tx was committed/rolled back
                // This would usually mean the completion of the request is hanging for some reason
                ControllerLogger.MGMT_OP_LOGGER.tracef("completeTx (post-commit cancel) for %d", getOperationId());
                cancel(context);
            } else if (postPrepareRaceResponse == null) {
                txCompleted = true;
                if (activeTx != null) {
                    try {
                        assert responseChannel == null;
                        responseChannel = context;
                        ControllerLogger.MGMT_OP_LOGGER.tracef("completeTx (%s) for %d", commit, getOperationId());
                        if (commit) {
                            activeTx.commit();
                        } else {
                            activeTx.rollback();
                        }
                    } finally {
                        txCompletedLatch.countDown();
                    }
                } // else when the prepare call came in rollbackOnPrepare was true. That means this was
                  // a 2nd cancellation request. We already cancelled in the if (!prepared) block above
                  // when the first request came in and doing it again will do nothing, so ignore this.
            } else {
                assert responseChannel == null;
                responseChannel = context;
                ControllerLogger.MGMT_OP_LOGGER.tracef("completeTx (%s) for %d received after a post-prepare response " +
                        "had already been cached; sending the cached response", commit, getOperationId());
                completed(postPrepareRaceResponse);
            }
        }

        /**
         * Handles sending a failure response to the remote client. If operation has already been prepared
         * this method simply delegates to {@link #completed(OperationResponse)}.
         * @param response the failure response ModelNode to send
         */
        synchronized void failed(final ModelNode response) {
            if(prepared) {
                // in case commit or rollback throws an exception, to conform with the API we still send an operation-completed message
                completed(OperationResponse.Factory.createSimple(response));
            } else {
                // Failure before prepare. So send a response to the original request
                assert responseChannel != null;
                ControllerLogger.MGMT_OP_LOGGER.tracef("sending pre-prepare failed response for %d  --- interrupted: %s", getOperationId(), (Object) Thread.currentThread().isInterrupted());
                try {
                    sendResponse(responseChannel, ModelControllerProtocol.PARAM_OPERATION_FAILED, response);
                    responseChannel = null; // we've now sent a response to the original request, so we can't use this one further
                } catch (IOException e) {
                    ControllerLogger.MGMT_OP_LOGGER.failedSendingFailedResponse(e, response, getOperationId());
                } finally {
                    getResultHandler().done(null);
                }
            }
        }

        /**
         * Sends the final response to the remote client after the prepare phase has been executed.
         * This should be called whether the outcome was successful or not.
         */
        synchronized void completed(final OperationResponse response) {

            assert prepared;
            if (responseChannel != null) {
                // Normal case, where a COMPLETE_TX_REQUEST came in after the prepare() call and
                // established a new responseChannel so the client can correlate the response
                ControllerLogger.MGMT_OP_LOGGER.tracef("sending completed response %s for %d  --- interrupted: %s", response.getResponseNode(), getOperationId(), Thread.currentThread().isInterrupted());

                streamSupport.registerStreams(operation.getOperationId(), response.getInputStreams());

                try {
                    sendResponse(responseChannel, ModelControllerProtocol.PARAM_OPERATION_COMPLETED, response.getResponseNode());
                    responseChannel = null; // we've now sent a response to the COMPLETE_TX_REQUEST, so we can't use this one further
                } catch (IOException e) {
                    ControllerLogger.MGMT_OP_LOGGER.failedSendingCompletedResponse(e, response.getResponseNode(), getOperationId());
                } finally {
                    getResultHandler().done(null);
                }
            } else {
                // We were cancelled or somehow failed after sending our prepare() message but before we got a COMPLETE_TX_REQUEST.
                // The client will not be able to deal with any response until it sends a COMPLETE_TX_REQUEST
                // (which is why we null out responseChannel in prepare()). So, just cache this response
                // so we can send it in completeTx when the COMPLETE_TX_REQUEST comes in.
                assert postPrepareRaceResponse == null; // else we got called twice locally!
                ControllerLogger.MGMT_OP_LOGGER.tracef("received a post-prepare response for %d but no " +
                        "COMPLETE_TX_REQUEST has been received; caching the response", getOperationId());
                postPrepareRaceResponse = response;
            }
        }

        /** Asynchronously invokes cancel on the result handler for the operation */
        private void cancel(final ManagementRequestContext<ExecuteRequestContext> context) {
            context.executeAsync(new ManagementRequestContext.AsyncTask<ExecuteRequestContext>() {
                @Override
                public void execute(ManagementRequestContext<ExecuteRequestContext> executeRequestContextManagementRequestContext) throws Exception {
                    operation.getResultHandler().cancel();
                }
            }, false);
        }

    }

    /**
     * Send an operation response.
     *
     * @param context the request context
     * @param responseType the response type
     * @param response the operation response
     * @throws java.io.IOException for any error
     */
    static void sendResponse(final ManagementRequestContext<ExecuteRequestContext> context, final byte responseType, final ModelNode response) throws IOException {

        // WFLY-3090 Protect the communication channel from getting closed due to administrative
        // cancellation of the management op by using a separate thread to send
        final CountDownLatch latch = new CountDownLatch(1);
        final IOExceptionHolder exceptionHolder = new IOExceptionHolder();
        boolean accepted = context.executeAsync(new AsyncTask<TransactionalProtocolOperationHandler.ExecuteRequestContext>() {

            @Override
            public void execute(final ManagementRequestContext<ExecuteRequestContext> context) throws Exception {
                FlushableDataOutput output = null;
                try {
                    MGMT_OP_LOGGER.tracef("Transmitting response for %d", context.getOperationId());
                    final ManagementResponseHeader header = ManagementResponseHeader.create(context.getRequestHeader());
                    output = context.writeMessage(header);
                    // response type
                    output.writeByte(responseType);
                    // operation result
                    response.writeExternal(output);
                    // response end
                    output.writeByte(ManagementProtocol.RESPONSE_END);
                    output.close();
                } catch (IOException toCache) {
                    exceptionHolder.exception = toCache;
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
                throw exceptionHolder.exception;
            }
        }
    }

    private static class IOExceptionHolder {
        private IOException exception;
    }

    /**
     * Provides a Supplier execution in a doPrivileged block if a security manager is checking privileges
     */
    private static Execution privilegedExecution() {
        return WildFlySecurityManager.isChecking() ? Execution.PRIVILEGED : Execution.NON_PRIVILEGED;
    }

    private interface Execution {
        <T> T execute(Supplier<T> supplier);

        Execution NON_PRIVILEGED = new Execution() {
            @Override
            public <T> T execute(Supplier<T> supplier) {
                return supplier.get();
            }
        };

        Execution PRIVILEGED = new Execution() {
            @Override
            public <T> T execute(Supplier<T> supplier) {
                try {
                    return doPrivileged((PrivilegedExceptionAction<T>) () -> NON_PRIVILEGED.execute(supplier));
                } catch (PrivilegedActionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    } else if (cause instanceof Error) {
                        throw (Error) cause;
                    } else {
                        // Not possible as Function doesn't throw any checked exception
                        throw new RuntimeException(cause);
                    }
                }
            }
        };
    }

}
