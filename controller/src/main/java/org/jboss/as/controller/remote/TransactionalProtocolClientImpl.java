/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.remote;

import static java.security.AccessController.doPrivileged;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTACHED_STREAMS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CANCELLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.remote.IdentityAddressProtocolUtil.write;
import static org.jboss.as.protocol.mgmt.ProtocolUtils.expectHeader;

import java.io.DataInput;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.as.controller.AccessAuditContext;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.access.InVmAccess;
import org.jboss.as.controller.client.MessageSeverity;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.impl.AbstractDelegatingAsyncFuture;
import org.jboss.as.controller.client.impl.ModelControllerProtocol;
import org.jboss.as.controller.client.impl.OperationResponseProxy;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.protocol.mgmt.AbstractManagementRequest;
import org.jboss.as.protocol.mgmt.ActiveOperation;
import org.jboss.as.protocol.mgmt.FlushableDataOutput;
import org.jboss.as.protocol.mgmt.ManagementChannelAssociation;
import org.jboss.as.protocol.mgmt.ManagementChannelHandler;
import org.jboss.as.protocol.mgmt.ManagementProtocol;
import org.jboss.as.protocol.mgmt.ManagementRequestContext;
import org.jboss.as.protocol.mgmt.ManagementRequestHandler;
import org.jboss.as.protocol.mgmt.ManagementRequestHandlerFactory;
import org.jboss.as.protocol.mgmt.ManagementRequestHeader;
import org.jboss.as.protocol.mgmt.ManagementResponseHeader;
import org.jboss.dmr.ModelNode;
import org.jboss.threads.AsyncFuture;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Base implementation for the transactional protocol.
 * <p />
 * This implementation uses Management requests to keep operation's transaniolabitility as follows:
 * <ul>
 * <li>Initiates the transaction with an {@link ExecuteRequest}, which is handled on the remote side via an
 * {@link TransactionalProtocolOperationHandler.ExecuteRequestHandler}. This handler executes the operation on the remote
 * controller and returns the prepared response. The operation is suspended on the remote side waiting for the client until a
 * commit or rollaback is received.</li>
 * <li>Once the prepared response is received on the client side, the operation is committed or rollback on the client side
 * which sends the decided TX status to the remote side by using a {@link CompleteTxRequest}. This request is handled on the
 * remote side via an {@link TransactionalProtocolOperationHandler.CompleteTxOperationHandler}</li>
 * <li>Once the remote side receives the TX status from the client, the prepared operation continues the complete step
 * executions and the final result is send back to the client.</li>
 * </ul>
 *
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class TransactionalProtocolClientImpl implements ManagementRequestHandlerFactory, TransactionalProtocolClient {

    private static final File javaTempDir = new File(WildFlySecurityManager.getPropertyPrivileged("java.io.tmpdir", null));

    private final File tempDir;
    private final ManagementChannelAssociation channelAssociation;

    public TransactionalProtocolClientImpl(final ManagementChannelAssociation channelAssociation) {
        assert channelAssociation != null;
        this.channelAssociation = channelAssociation;
        final File temp = channelAssociation.getAttachments().getAttachment(ManagementChannelHandler.TEMP_DIR);
        if (temp != null && temp.isDirectory()) {
            tempDir = temp;
        } else {
            tempDir = javaTempDir;
        }
    }

    /** {@inheritDoc} */
    @Override
    public ManagementRequestHandler<?, ?> resolveHandler(RequestHandlerChain handlers, ManagementRequestHeader header) {
        final byte operationType = header.getOperationId();
        if (operationType == ModelControllerProtocol.HANDLE_REPORT_REQUEST) {
            return new HandleReportRequestHandler();
        } else if (operationType == ModelControllerProtocol.GET_INPUTSTREAM_REQUEST) {
            return ReadAttachmentInputStreamRequestHandler.INSTANCE;
        }
        return handlers.resolveNext();
    }

    @Override
    public AsyncFuture<OperationResponse> execute(TransactionalOperationListener<Operation> listener, ModelNode operation, OperationMessageHandler messageHandler, OperationAttachments attachments) throws IOException {
        final Operation wrapper = TransactionalProtocolHandlers.wrap(operation, messageHandler, attachments);
        return execute(listener, wrapper);
    }

    @Override
    public <T extends Operation> AsyncFuture<OperationResponse> execute(TransactionalOperationListener<T> listener, T operation) throws IOException {
        AccessAuditContext accessAuditContext = WildFlySecurityManager.isChecking()
                ? doPrivileged((PrivilegedAction<AccessAuditContext>) AccessAuditContext::currentAccessAuditContext)
                : AccessAuditContext.currentAccessAuditContext();
        final ExecuteRequestContext context = new ExecuteRequestContext(new OperationWrapper<>(listener, operation),
                accessAuditContext != null ? accessAuditContext.getSecurityIdentity() : null,
                accessAuditContext != null ? accessAuditContext.getRemoteAddress() : null,
                tempDir,
                InVmAccess.isInVmCall());
        final ActiveOperation<OperationResponse, ExecuteRequestContext> op = channelAssociation.initializeOperation(context, context);
        final AtomicBoolean cancelSent = new AtomicBoolean();
        final AsyncFuture<OperationResponse> result = new AbstractDelegatingAsyncFuture<OperationResponse>(op.getResult()) {
            @Override
            public synchronized void asyncCancel(boolean interruptionDesired) {
                if (!cancelSent.get()) {
                    try {
                        // Execute
                        channelAssociation.executeRequest(op, new CompleteTxRequest(ModelControllerProtocol.PARAM_ROLLBACK, channelAssociation));
                        cancelSent.set(true);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };
        context.initialize(result);
        channelAssociation.executeRequest(op, new ExecuteRequest());
        return result;
    }

    /**
     * Request for the the remote {@link TransactionalProtocolOperationHandler.ExecuteRequestHandler}.
     *
     * The required response is either a:
     *  - {@link org.jboss.as.controller.client.impl.ModelControllerProtocol#PARAM_OPERATION_FAILED}, which will complete the operation right away
     *  - or {@link org.jboss.as.controller.client.impl.ModelControllerProtocol#PARAM_OPERATION_PREPARED}
     */
    private class ExecuteRequest extends AbstractManagementRequest<OperationResponse, ExecuteRequestContext> {

        @Override
        public byte getOperationType() {
            return ModelControllerProtocol.EXECUTE_TX_REQUEST;
        }

        @Override
        public void sendRequest(final ActiveOperation.ResultHandler<OperationResponse> resultHandler,
                                final ManagementRequestContext<ExecuteRequestContext> context) throws IOException {

            ControllerLogger.MGMT_OP_LOGGER.tracef("sending ExecuteRequest for %d", context.getOperationId());
            // WFLY-3090 Protect the communication channel from getting closed due to administrative
            // cancellation of the management op by using a separate thread to send
            context.executeAsync(new ManagementRequestContext.AsyncTask<ExecuteRequestContext>() {
                @Override
                public void execute(ManagementRequestContext<ExecuteRequestContext> context) throws Exception {
                    sendRequestInternal(resultHandler, context);
                }
            }, false);

        }

        @Override
        protected void sendRequest(final ActiveOperation.ResultHandler<OperationResponse> resultHandler,
                                   final ManagementRequestContext<ExecuteRequestContext> context,
                                   final FlushableDataOutput output) throws IOException {

            ControllerLogger.MGMT_OP_LOGGER.tracef("transmitting ExecuteRequest for %d", context.getOperationId());

            // Write the operation
            final ExecuteRequestContext executionContext = context.getAttachment();
            final List<InputStream> streams = executionContext.getInputStreams();
            final ModelNode operation = executionContext.getOperation();
            int inputStreamLength = 0;
            if (streams != null) {
                inputStreamLength = streams.size();
            }
            output.write(ModelControllerProtocol.PARAM_OPERATION);
            operation.writeExternal(output);
            output.write(ModelControllerProtocol.PARAM_INPUTSTREAMS_LENGTH);
            output.writeInt(inputStreamLength);

            final Boolean sendIdentity = channelAssociation.getAttachments().getAttachment(SEND_IDENTITY);
            if (sendIdentity != null && sendIdentity) {
                ExecuteRequestContext attachment = context.getAttachment();
                write(output, attachment.getSecurityIdentity(), attachment.getRemoteAddress());
            }

            final Boolean sendInVm = channelAssociation.getAttachments().getAttachment(SEND_IN_VM);
            if (sendInVm != null && sendInVm) {
                ExecuteRequestContext attachment = context.getAttachment();
                output.writeByte(ModelControllerProtocol.PARAM_IN_VM_CALL);
                output.writeBoolean(attachment.isInVmCall());
            }
        }

        @Override
        public void handleRequest(final DataInput input, final ActiveOperation.ResultHandler<OperationResponse> resultHandler, final ManagementRequestContext<ExecuteRequestContext> context) throws IOException {
            ControllerLogger.MGMT_OP_LOGGER.tracef("received response to ExecuteRequest for %d", context.getOperationId());
            final byte responseType = input.readByte();
            final ModelNode response = new ModelNode();
            response.readExternal(input);
            // If not prepared the operation failed
            final boolean prepared = responseType == ModelControllerProtocol.PARAM_OPERATION_PREPARED;
            final ExecuteRequestContext executeRequestContext = context.getAttachment();
            if(prepared) {
                executeRequestContext.operationPrepared(new ModelController.OperationTransaction() {

                    @Override
                    public void rollback() {
                        done(false);
                    }

                    @Override
                    public void commit() {
                        done(true);
                    }

                    private void done(boolean commit) {
                        final byte status = commit ? ModelControllerProtocol.PARAM_COMMIT : ModelControllerProtocol.PARAM_ROLLBACK;
                        try {
                            // Send the CompleteTxRequest
                            channelAssociation.executeRequest(context.getOperationId(), new CompleteTxRequest(status, channelAssociation));
                        } catch (Exception e) {
                            resultHandler.failed(e);
                        }
                    }
                }, response);
            } else {
                // Failed
                executeRequestContext.operationFailed(response);
                resultHandler.done(OperationResponse.Factory.createSimple(response));
            }
        }

        private void sendRequestInternal(ActiveOperation.ResultHandler<OperationResponse> resultHandler,
                                         ManagementRequestContext<ExecuteRequestContext> context) throws IOException {
            super.sendRequest(resultHandler, context);
        }
    }

    /**
     * Signal the remote controller to either commit or rollback. The response has to be a
     * {@link org.jboss.as.controller.client.impl.ModelControllerProtocol#PARAM_OPERATION_COMPLETED}.
     */
    private static class CompleteTxRequest extends AbstractManagementRequest<OperationResponse, ExecuteRequestContext> {

        private final byte status;
        private final ManagementChannelAssociation channelAssociation;
        private CompleteTxRequest(byte status, ManagementChannelAssociation channelAssociation) {
            this.status = status;
            this.channelAssociation = channelAssociation;
        }

        @Override
        public byte getOperationType() {
            return ModelControllerProtocol. COMPLETE_TX_REQUEST;
        }

        @Override
        public void sendRequest(final ActiveOperation.ResultHandler<OperationResponse> resultHandler,
                                final ManagementRequestContext<ExecuteRequestContext> context) throws IOException {

            ControllerLogger.MGMT_OP_LOGGER.tracef("sending CompleteTxRequest for %d", context.getOperationId());
            context.executeAsync(new ManagementRequestContext.AsyncTask<ExecuteRequestContext>() {
                @Override
                public void execute(ManagementRequestContext<ExecuteRequestContext> context) throws Exception {
                    sendRequestInternal(resultHandler, context);
                }
            }, false);

        }

        @Override
        protected void sendRequest(final ActiveOperation.ResultHandler<OperationResponse> resultHandler, final ManagementRequestContext<ExecuteRequestContext> context, final FlushableDataOutput output) throws IOException {

            ControllerLogger.MGMT_OP_LOGGER.tracef("transmitting CompleteTxRequest (%s) for %d", status != ModelControllerProtocol.PARAM_ROLLBACK, context.getOperationId());
            output.write(status);
        }

        @Override
        public void handleRequest(final DataInput input, final ActiveOperation.ResultHandler<OperationResponse> resultHandler, final ManagementRequestContext<ExecuteRequestContext> context) throws IOException {
            ControllerLogger.MGMT_OP_LOGGER.tracef("received response to CompleteTxRequest (%s) for %d", status != ModelControllerProtocol.PARAM_ROLLBACK, context.getOperationId());
            // We only accept operationCompleted responses
            expectHeader(input, ModelControllerProtocol.PARAM_OPERATION_COMPLETED);
            final ModelNode responseNode = new ModelNode();
            responseNode.readExternal(input);
            // Complete the operation
            resultHandler.done(createOperationResponse(responseNode, channelAssociation, context.getOperationId()));
        }

        private void sendRequestInternal(ActiveOperation.ResultHandler<OperationResponse> resultHandler,
                                         ManagementRequestContext<ExecuteRequestContext> context) throws IOException {
            super.sendRequest(resultHandler, context);
        }
    }

    /**
     * Handles {@link org.jboss.as.controller.client.OperationMessageHandler#handleReport(org.jboss.as.controller.client.MessageSeverity, String)} calls
     * done in the remote target controller
     */
    private static class HandleReportRequestHandler implements ManagementRequestHandler<ModelNode, ExecuteRequestContext> {

        @Override
        public void handleRequest(final DataInput input, final ActiveOperation.ResultHandler<ModelNode> resultHandler, final ManagementRequestContext<ExecuteRequestContext> context) throws IOException {
            expectHeader(input, ModelControllerProtocol.PARAM_MESSAGE_SEVERITY);
            final MessageSeverity severity = Enum.valueOf(MessageSeverity.class, input.readUTF());
            expectHeader(input, ModelControllerProtocol.PARAM_MESSAGE);
            final String message = input.readUTF();
            expectHeader(input, ManagementProtocol.REQUEST_END);

            final ExecuteRequestContext requestContext = context.getAttachment();
            // perhaps execute async
            final OperationMessageHandler handler = requestContext.getMessageHandler();
            handler.handleReport(severity, message);
        }

    }

    /**
     * Handles reads on the inputstreams returned by {@link org.jboss.as.controller.client.OperationAttachments#getInputStreams()}
     * done in the remote target controller
     */
    private static class ReadAttachmentInputStreamRequestHandler implements ManagementRequestHandler<ModelNode, ExecuteRequestContext> {

        static final ReadAttachmentInputStreamRequestHandler INSTANCE = new ReadAttachmentInputStreamRequestHandler();

        @Override
        public void handleRequest(final DataInput input, final ActiveOperation.ResultHandler<ModelNode> resultHandler,
                              final ManagementRequestContext<ExecuteRequestContext> context) throws IOException {
            // Read the inputStream index
            expectHeader(input, ModelControllerProtocol.PARAM_INPUTSTREAM_INDEX);
            final int index = input.readInt();

            context.executeAsync(new ManagementRequestContext.AsyncTask<ExecuteRequestContext>() {
                @Override
                public void execute(final ManagementRequestContext<ExecuteRequestContext> context) throws Exception {
                    final ExecuteRequestContext exec = context.getAttachment();
                    final ManagementRequestHeader header = ManagementRequestHeader.class.cast(context.getRequestHeader());
                    final ManagementResponseHeader response = new ManagementResponseHeader(header.getVersion(), header.getRequestId(), null);
                    final InputStream is = exec.getAttachments().getInputStreams().get(index);
                    try {
                        final File temp = copyStream(is, exec.tempDir);
                        try {
                            final FlushableDataOutput output = context.writeMessage(response);
                            try {
                                output.writeByte(ModelControllerProtocol.PARAM_INPUTSTREAM_LENGTH);
                                output.writeInt((int) temp.length()); // the int is required by the protocol
                                output.writeByte(ModelControllerProtocol.PARAM_INPUTSTREAM_CONTENTS);
                                final FileInputStream fis = new FileInputStream(temp);
                                try {
                                    StreamUtils.copyStream(fis, output);
                                    fis.close();
                                } finally {
                                    StreamUtils.safeClose(fis);
                                }
                                output.writeByte(ManagementProtocol.RESPONSE_END);
                                output.close();
                            } finally {
                                StreamUtils.safeClose(output);
                            }
                        } finally {
                            temp.delete();
                        }
                    } finally {
                        // the caller is responsible for closing the input streams
                        // StreamUtils.safeClose(is);
                    }
                }
            });
        }

        protected File copyStream(final InputStream is, final File tempDir) throws IOException {
            final File temp = File.createTempFile("upload", "temp", tempDir);
            if (is != null) {
                final FileOutputStream os = new FileOutputStream(temp);
                try {
                    StreamUtils.copyStream(is, os);
                    os.close();
                } finally {
                    StreamUtils.safeClose(os);
                }
            }
            return temp;
        }
    }

    static class ExecuteRequestContext implements ActiveOperation.CompletedCallback<OperationResponse> {
        final OperationWrapper<?> wrapper;
        final AtomicBoolean completed = new AtomicBoolean(false);
        final SecurityIdentity securityIdentity;
        final InetAddress remoteAddress;
        final File tempDir;
        final boolean inVmCall;

        ExecuteRequestContext(OperationWrapper<?> operationWrapper, SecurityIdentity securityIdentity, InetAddress remoteAddress, File tempDir, boolean inVmCall) {
            this.wrapper = operationWrapper;
            this.securityIdentity = securityIdentity;
            this.remoteAddress = remoteAddress;
            this.tempDir = tempDir;
            this.inVmCall = inVmCall;
        }

        void initialize(final AsyncFuture<OperationResponse> result) {
            wrapper.future = result;
        }

        OperationMessageHandler getMessageHandler() {
            return wrapper.getMessageHandler();
        }

        ModelNode getOperation() {
            return wrapper.getOperation();
        }

        OperationAttachments getAttachments() {
            return wrapper.getAttachments();
        }

        List<InputStream> getInputStreams() {
            final OperationAttachments attachments = getAttachments();
            if(attachments == null) {
                return Collections.emptyList();
            }
            return attachments.getInputStreams();
        }

        SecurityIdentity getSecurityIdentity() {
            return securityIdentity;
        }

        InetAddress getRemoteAddress() {
            return remoteAddress;
        }

        @Override
        public synchronized void completed(final OperationResponse result) {
            if(completed.compareAndSet(false, true)) {
                wrapper.completed(result);
            }
        }

        @Override
        public void failed(Exception e) {
            operationFailed(getFailureResponse(FAILED, e.getMessage()));
        }

        @Override
        public void cancelled() {
            operationFailed(getResponse(CANCELLED));
        }

        synchronized void operationFailed(final ModelNode response) {
            if(completed.compareAndSet(false, true)) {
                wrapper.failed(response);
            }
        }

        synchronized void operationPrepared(final ModelController.OperationTransaction transaction, final ModelNode result) {
            wrapper.prepared(transaction, result);
        }

        public boolean isInVmCall() {
            return inVmCall;
        }
    }

    private static class OperationWrapper<T extends Operation> {

        private final T operation;
        private final TransactionalOperationListener<T> listener;
        private AsyncFuture<OperationResponse> future;

        OperationWrapper(TransactionalOperationListener<T> listener, T operation) {
            this.listener = listener;
            this.operation = operation;
        }

        OperationMessageHandler getMessageHandler() {
            return operation.getMessageHandler();
        }

        ModelNode getOperation() {
            return operation.getOperation();
        }

        OperationAttachments getAttachments() {
            return operation.getAttachments();
        }

        void prepared(final ModelController.OperationTransaction transaction, final ModelNode result) {
            final PreparedOperation<T> preparedOperation = new PreparedOperationImpl<T>(operation, result, future, transaction);
            listener.operationPrepared(preparedOperation);
        }

        void completed(final OperationResponse response) {
            listener.operationComplete(operation, response);
        }

        void failed(final ModelNode response) {
            listener.operationFailed(operation, response);
        }

    }



    private static OperationResponse createOperationResponse(ModelNode simpleResponse, ManagementChannelAssociation channelAssociation, int operationId) {
        final ModelNode streamHeader =  simpleResponse.hasDefined(RESPONSE_HEADERS) && simpleResponse.get(RESPONSE_HEADERS).hasDefined(ATTACHED_STREAMS)
                ? simpleResponse.get(RESPONSE_HEADERS, ATTACHED_STREAMS)
                : null;
        if (streamHeader != null && streamHeader.asInt() > 0) {
            return OperationResponseProxy.create(simpleResponse, channelAssociation, operationId, streamHeader);
        } else {
            return OperationResponse.Factory.createSimple(simpleResponse);
        }
    }

    static class PreparedOperationImpl<T extends Operation> implements PreparedOperation<T> {

        private final T operation;
        private final ModelNode preparedResult;
        private final AsyncFuture<OperationResponse> finalResult;
        private final ModelController.OperationTransaction transaction;

        protected PreparedOperationImpl(T operation, ModelNode preparedResult, AsyncFuture<OperationResponse> finalResult, ModelController.OperationTransaction transaction) {
            assert finalResult != null : "null result";
            this.operation = operation;
            this.preparedResult = preparedResult;
            this.finalResult = finalResult;
            this.transaction = transaction;
        }

        @Override
        public T getOperation() {
            return operation;
        }

        @Override
        public ModelNode getPreparedResult() {
            return preparedResult;
        }

        @Override
        public boolean isFailed() {
            return false;
        }

        @Override
        public boolean isTimedOut() {
            return false;
        }

        @Override
        public boolean isDone() {
            return finalResult.isDone();
        }

        @Override
        public AsyncFuture<OperationResponse> getFinalResult() {
            return finalResult;
        }

        @Override
        public void commit() {
            transaction.commit();
        }

        @Override
        public void rollback() {
            transaction.rollback();
        }

    }

    static ModelNode getFailureResponse(final String outcome, final String message) {
        final ModelNode response = new ModelNode();
        response.get(OUTCOME).set(outcome);
        if(message != null) response.get(FAILURE_DESCRIPTION).set(message);
        return response;
    }

    static ModelNode getResponse(final String outcome) {
        return getFailureResponse(outcome, null);
    }

}
