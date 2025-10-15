/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.client.impl;

import static org.jboss.as.controller.client.helpers.ClientConstants.ATTACHED_STREAMS;
import static org.jboss.as.controller.client.helpers.ClientConstants.RESPONSE_HEADERS;
import static org.jboss.as.protocol.mgmt.ProtocolUtils.expectHeader;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jboss.as.controller.client.MessageSeverity;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.logging.ControllerClientLogger;
import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.protocol.mgmt.AbstractManagementRequest;
import org.jboss.as.protocol.mgmt.ActiveOperation;
import org.jboss.as.protocol.mgmt.FlushableDataOutput;
import org.jboss.as.protocol.mgmt.ManagementChannelAssociation;
import org.jboss.as.protocol.mgmt.ManagementProtocol;
import org.jboss.as.protocol.mgmt.ManagementRequest;
import org.jboss.as.protocol.mgmt.ManagementRequestContext;
import org.jboss.as.protocol.mgmt.ManagementRequestHandler;
import org.jboss.as.protocol.mgmt.ManagementRequestHandlerFactory;
import org.jboss.as.protocol.mgmt.ManagementRequestHeader;
import org.jboss.as.protocol.mgmt.ManagementResponseHeader;
import org.jboss.dmr.ModelNode;
import org.jboss.threads.AsyncFuture;


/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public abstract class AbstractModelControllerClient implements ModelControllerClient, ManagementRequestHandlerFactory {

    private static final ManagementRequestHandler<ModelNode, OperationExecutionContext> MESSAGE_HANDLER = new HandleReportRequestHandler();
    private static final ManagementRequestHandler<ModelNode, OperationExecutionContext> GET_INPUT_STREAM = new ReadAttachmentInputStreamRequestHandler();

    private static final OperationMessageHandler NO_OP_HANDLER = OperationMessageHandler.DISCARD;

    /**
     * Get the mgmt channel association.
     *
     * @return the channel association
     */
    protected abstract ManagementChannelAssociation getChannelAssociation();

    @Override
    public ModelNode execute(final ModelNode operation) throws IOException {
        return responseNodeOnly(executeForResult(OperationExecutionContext.create(operation)));
    }

    @Override
    public ModelNode execute(final Operation operation) throws IOException {
        return responseNodeOnly(executeForResult(OperationExecutionContext.create(operation)));
    }

    @Override
    public ModelNode execute(final ModelNode operation, final OperationMessageHandler messageHandler) throws IOException {
        return responseNodeOnly(executeForResult(OperationExecutionContext.create(operation, messageHandler)));
    }

    @Override
    public ModelNode execute(Operation operation, OperationMessageHandler messageHandler) throws IOException {
        return responseNodeOnly(executeForResult(OperationExecutionContext.create(operation, messageHandler)));
    }

    @Override
    public OperationResponse executeOperation(Operation operation, OperationMessageHandler messageHandler) throws IOException {
        return executeForResult(OperationExecutionContext.create(operation, messageHandler));
    }

    @Override
    public CompletableFuture<ModelNode> executeAsync(final ModelNode operation, final OperationMessageHandler messageHandler) {
        try {
            return execute(OperationExecutionContext.create(operation, messageHandler),
                    AbstractModelControllerClient::responseNodeOnlyWitRuntimeException);
        } catch (IOException e)  {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<ModelNode> executeAsync(final Operation operation, final OperationMessageHandler messageHandler) {
        try {
            return execute(OperationExecutionContext.create(operation, messageHandler),
                    AbstractModelControllerClient::responseNodeOnlyWitRuntimeException);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<OperationResponse> executeOperationAsync(Operation operation, OperationMessageHandler messageHandler) {
        try {
            return execute(OperationExecutionContext.create(operation, messageHandler), opResponse -> opResponse);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ManagementRequestHandler<?, ?> resolveHandler(RequestHandlerChain handlers, ManagementRequestHeader header) {
        final byte operationType = header.getOperationId();
        if (operationType == ModelControllerProtocol.HANDLE_REPORT_REQUEST) {
            return MESSAGE_HANDLER;
        } else if (operationType == ModelControllerProtocol.GET_INPUTSTREAM_REQUEST) {
            return GET_INPUT_STREAM;
        }
        return handlers.resolveNext();
    }

    /**
     * Execute for result.
     *
     * @param executionContext the execution context
     * @return the result
     * @throws IOException for any error
     */
    private OperationResponse executeForResult(final OperationExecutionContext executionContext) throws IOException {
        try {
            return execute(executionContext, opResponse -> opResponse).get();
        } catch(Exception e) {
            throw new IOException(e);
        }
    }

    /** Extracts the response node from an OperationResponse and returns it after first closing the OperationResponse */
    private static ModelNode responseNodeOnly(OperationResponse or) throws IOException {
        ModelNode result = or.getResponseNode();
        or.close();
        return result;
    }

    private static ModelNode responseNodeOnlyWitRuntimeException(OperationResponse or) {
        try {
            return responseNodeOnly(or);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Execute a request.
     *
     * @param executionContext the execution context
     * @return the future result
     * @throws IOException
     */
    private <T> CompletableFuture<T> execute(final OperationExecutionContext executionContext,
                                             Function<OperationResponse, T> transformer) throws IOException {
        return executeRequest(new AbstractManagementRequest<>() {

            @Override
            public byte getOperationType() {
                return ModelControllerProtocol.EXECUTE_ASYNC_CLIENT_REQUEST;
            }

            @Override
            protected void sendRequest(final ActiveOperation.ResultHandler<OperationResponse> resultHandler,
                                       final ManagementRequestContext<OperationExecutionContext> context,
                                       final FlushableDataOutput output) throws IOException {
                // Write the operation
                final List<InputStream> streams = executionContext.operation.getInputStreams();
                final ModelNode operation = executionContext.operation.getOperation();
                int inputStreamLength = 0;
                if (streams != null) {
                    inputStreamLength = streams.size();
                }
                output.write(ModelControllerProtocol.PARAM_OPERATION);
                operation.writeExternal(output);
                output.write(ModelControllerProtocol.PARAM_INPUTSTREAMS_LENGTH);
                output.writeInt(inputStreamLength);
            }

            @Override
            public void handleRequest(final DataInput input, final ActiveOperation.ResultHandler<OperationResponse> resultHandler, final ManagementRequestContext<OperationExecutionContext> context) throws IOException {
                expectHeader(input, ModelControllerProtocol.PARAM_RESPONSE);
                final ModelNode node = new ModelNode();
                node.readExternal(input);
                resultHandler.done(getOperationResponse(node, context.getOperationId()));
                expectHeader(input, ManagementProtocol.RESPONSE_END);
            }
        }, executionContext, transformer);
    }

    private static class ReadAttachmentInputStreamRequestHandler implements ManagementRequestHandler<ModelNode, OperationExecutionContext> {

        @Override
        public void handleRequest(final DataInput input, final ActiveOperation.ResultHandler<ModelNode> resultHandler,
                              final ManagementRequestContext<OperationExecutionContext> context) throws IOException {
            // Read the inputStream index
            expectHeader(input, ModelControllerProtocol.PARAM_INPUTSTREAM_INDEX);
            final int index = input.readInt();
            context.executeAsync(new ManagementRequestContext.AsyncTask<>() {
                @Override
                public void execute(final ManagementRequestContext<OperationExecutionContext> taskContext) throws Exception {
                    final OperationExecutionContext exec = taskContext.getAttachment();
                    final ManagementRequestHeader header = (ManagementRequestHeader) taskContext.getRequestHeader();
                    final ManagementResponseHeader response = new ManagementResponseHeader(header.getVersion(), header.getRequestId(), null);
                    final InputStreamEntry entry = exec.getStream(index);
                    synchronized (entry) {
                        // Initialize the stream entry
                        final int size = entry.initialize();
                        try {
                            final FlushableDataOutput output = taskContext.writeMessage(response);
                            try {
                                output.writeByte(ModelControllerProtocol.PARAM_INPUTSTREAM_LENGTH);
                                output.writeInt(size);
                                output.writeByte(ModelControllerProtocol.PARAM_INPUTSTREAM_CONTENTS);
                                entry.copyStream(output);
                                output.writeByte(ManagementProtocol.RESPONSE_END);
                                output.close();
                            } finally {
                                StreamUtils.safeClose(output);
                            }
                        } finally {
                            // the caller is responsible for closing the input streams
                            // StreamUtils.safeClose(is);
                        }
                    }
                }
            });
        }

    }

    private static class HandleReportRequestHandler implements ManagementRequestHandler<ModelNode, OperationExecutionContext> {

        @Override
        public void handleRequest(final DataInput input, final ActiveOperation.ResultHandler<ModelNode> resultHandler, final ManagementRequestContext<OperationExecutionContext> context) throws IOException {
            expectHeader(input, ModelControllerProtocol.PARAM_MESSAGE_SEVERITY);
            final MessageSeverity severity = Enum.valueOf(MessageSeverity.class, input.readUTF());
            expectHeader(input, ModelControllerProtocol.PARAM_MESSAGE);
            final String message = input.readUTF();
            expectHeader(input, ManagementProtocol.REQUEST_END);

            final OperationExecutionContext requestContext = context.getAttachment();
            // perhaps execute async
            final OperationMessageHandler handler = requestContext.getOperationMessageHandler();
            handler.handleReport(severity, message);
        }

    }

    <T> CompletableFuture<T> executeRequest(final ManagementRequest<OperationResponse, OperationExecutionContext> request,
                                            final OperationExecutionContext attachment,
                                            final Function<OperationResponse, T> transformer) throws IOException {
        final ActiveOperation<OperationResponse, OperationExecutionContext> activeOperation = getChannelAssociation().executeRequest(request, attachment, attachment);
        Consumer<Boolean> asyncCancelTask = interruptionAllowed -> executeCancelAsyncRequest(activeOperation);
        return activeOperation.getCompletableFuture(transformer, asyncCancelTask);
    }

    private void executeCancelAsyncRequest(ActiveOperation<?, ?> activeOperation) {
        if (activeOperation.getResult().getStatus() == AsyncFuture.Status.WAITING) {
            // Tell the remote side to cancel
            Integer operationId = activeOperation.getOperationId();
            try {
                getChannelAssociation().executeRequest(operationId, new CancelAsyncRequest());
            } catch (Exception e) {
                AsyncFuture.Status status = activeOperation.getResult().getStatus();
                if (status == AsyncFuture.Status.WAITING) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    } else {
                        throw new RuntimeException(e);
                    }
                } else {
                    // Most likely there was a race between our sending CancelAsyncRequest and the operation finishing.
                    // The operation is done though, so just debug log the failure and move on.
                    ControllerClientLogger.ROOT_LOGGER.debugf(e, "Executing CancelAsyncRequest for operation %s " +
                            "failed but the operation reached status %s, so the failure is being ignored", operationId, status);
                }
            }
        } // else the ActiveOperation is already done and there's nothing to cancel
    }

    static class OperationExecutionContext implements ActiveOperation.CompletedCallback<OperationResponse> {

        private final Operation operation;
        private final OperationMessageHandler handler;
        private final List<InputStreamEntry> streams;

        OperationExecutionContext(final Operation operation, final OperationMessageHandler handler) {
            this.operation = operation;
            this.handler = handler != null ? handler : NO_OP_HANDLER;
            this.streams = createStreamEntries(operation);
        }

        OperationMessageHandler getOperationMessageHandler() {
            return handler;
        }

        InputStreamEntry getStream(int index) {
            final InputStreamEntry entry = streams.get(index);
            if(entry == null) {
                // Hmm, a null input-stream should be a failure?
                return InputStreamEntry.EMPTY;
            }
            return entry;
        }

        @Override
        public void completed(OperationResponse result) {
            closeAttachments();
        }

        @Override
        public void failed(Exception e) {
            closeAttachments();
        }

        @Override
        public void cancelled() {
            closeAttachments();
        }

        private void closeAttachments() {
            for(final InputStreamEntry entry : streams) {
                StreamUtils.safeClose(entry);
            }
            if(operation.isAutoCloseStreams()) {
                StreamUtils.safeClose(operation);
            }
        }

        static OperationExecutionContext create(final ModelNode operation) {
            return create(new OperationBuilder(operation).build(), NO_OP_HANDLER);
        }

        static OperationExecutionContext create(final Operation operation) {
            return create(operation, NO_OP_HANDLER);
        }

        static OperationExecutionContext create(final ModelNode operation, final OperationMessageHandler handler) {
            return create(new OperationBuilder(operation).build(), handler);
        }

        static OperationExecutionContext create(final Operation operation, final OperationMessageHandler handler) {
            return new OperationExecutionContext(operation, handler);
        }

    }

    /**
     * Request cancelling the remote operation.
     */
    private static class CancelAsyncRequest extends AbstractManagementRequest<OperationResponse, OperationExecutionContext> {

        @Override
        public byte getOperationType() {
            return ModelControllerProtocol.CANCEL_ASYNC_REQUEST;
        }

        @Override
        protected void sendRequest(ActiveOperation.ResultHandler<OperationResponse> resultHandler, ManagementRequestContext<OperationExecutionContext> context, FlushableDataOutput output) {
            //
        }

        @Override
        public void handleRequest(DataInput input, ActiveOperation.ResultHandler<OperationResponse> resultHandler, ManagementRequestContext<OperationExecutionContext> context) {
            // Once the remote operation returns, we can set the cancelled status
            resultHandler.cancel();
        }
    }

    static List<InputStreamEntry> createStreamEntries(final Operation operation) {
        final List<InputStream> streams = operation.getInputStreams();
        if(streams.isEmpty()) {
            return Collections.emptyList();
        }
        final List<InputStreamEntry> entries = new ArrayList<>();
        final boolean autoClose = operation.isAutoCloseStreams();
        for(final InputStream stream : streams) {
            if(stream instanceof InputStreamEntry) {
                entries.add((InputStreamEntry) stream);
            } else {
                entries.add(new InputStreamEntry.CachingInMemoryFallbackStreamEntry(stream, autoClose));
            }
        }
        return entries;
    }

    private OperationResponse getOperationResponse(final ModelNode simpleResponse, final int batchId) {
        final ModelNode streamHeader =  simpleResponse.hasDefined(RESPONSE_HEADERS) && simpleResponse.get(RESPONSE_HEADERS).hasDefined(ATTACHED_STREAMS)
                ? simpleResponse.get(RESPONSE_HEADERS, ATTACHED_STREAMS)
                : null;
        if (streamHeader != null && streamHeader.asInt() > 0) {
            return OperationResponseProxy.create(simpleResponse, getChannelAssociation(), batchId, streamHeader);
        } else {
            return OperationResponse.Factory.createSimple(simpleResponse);
        }
    }

}
