/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.protocol.mgmt;

import java.io.DataInput;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.jboss.as.protocol.StreamUtils;
import org.jboss.as.protocol.logging.ProtocolLogger;
import org.jboss.as.protocol.mgmt.support.ManagementChannelShutdownHandle;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.MessageOutputStream;
import org.jboss.threads.AsyncFuture;

/**
 * Base class for {@link ManagementMessageHandler} implementations.
 *
 * @author Emanuel Muckenhuber
 */
public abstract class AbstractMessageHandler implements ManagementMessageHandler, ManagementChannelShutdownHandle, CloseHandler<Channel> {

    private static final ActiveOperation.CompletedCallback<?> NO_OP_CALLBACK = new ActiveOperation.CompletedCallback<Object>() {

        @Override
        public void completed(Object result) {
            //
        }

        @Override
        public void failed(Exception e) {
            //
        }

        @Override
        public void cancelled() {
            //
        }
    };

    static <T> ActiveOperation.CompletedCallback<T> getDefaultCallback() {
        //noinspection unchecked
        return (ActiveOperation.CompletedCallback<T>) NO_OP_CALLBACK;
    }

    static <T> ActiveOperation.CompletedCallback<T> getCheckedCallback(final ActiveOperation.CompletedCallback<T> callback) {
        if(callback == null) {
            return getDefaultCallback();
        }
        return callback;
    }

    private final ConcurrentMap<Integer, ActiveOperationImpl<?, ?>> activeRequests = new ConcurrentHashMap<> (16, 0.75f, Runtime.getRuntime().availableProcessors());
    private final ManagementBatchIdManager operationIdManager = new ManagementBatchIdManager.DefaultManagementBatchIdManager();

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final ExecutorService executorService;
    private final AtomicInteger requestID = new AtomicInteger();

    private final Map<Integer, ActiveRequest<?, ?>> requests = new ConcurrentHashMap<Integer, ActiveRequest<?, ?>>(16, 0.75f, Runtime.getRuntime().availableProcessors());

    // mutable variables, have to be guarded by the lock
    private int activeCount = 0;
    private volatile boolean shutdown = false;


    protected AbstractMessageHandler(final ExecutorService executorService) {
        if(executorService == null) {
            throw ProtocolLogger.ROOT_LOGGER.nullExecutor();
        }
        this.executorService = executorService;
    }

    /**
     * Receive a notification that the channel was closed.
     *
     * This is used for the {@link ManagementClientChannelStrategy.Establishing} since it might use multiple channels.
     *
     * @param closed the closed resource
     * @param e the exception which occurred during close, if any
     */
    public void handleChannelClosed(final Channel closed, final IOException e) {
        for(final ActiveOperationImpl<?, ?> activeOperation : activeRequests.values()) {
            if (activeOperation.getChannel() == closed) {
                // Only call cancel, to also interrupt still active threads
                activeOperation.getResultHandler().cancel();
            }
        }
    }

    /**
     * Is shutdown.
     *
     * @return {@code true} if the shutdown method was called, {@code false} otherwise
     */
    protected boolean isShutdown() {
        return shutdown;
    }

    /**
     * Prevent new active operations get registered.
     */
    @Override
    public void shutdown() {
        lock.lock(); try {
            shutdown = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdownNow() {
        shutdown();
        cancelAllActiveOperations();
    }

    /**
     * Await the completion of all currently active operations.
     *
     * @param timeout the timeout
     * @param unit the time unit
     * @return {@code } false if the timeout was reached and there were still active operations
     * @throws InterruptedException
     */
    @Override
    public boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = unit.toMillis(timeout) + System.currentTimeMillis();
        lock.lock(); try {
            assert shutdown;
            while(activeCount != 0) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    break;
                }
                condition.await(remaining, TimeUnit.MILLISECONDS);
            }
            boolean allComplete = activeCount == 0;
            if (!allComplete) {
                ProtocolLogger.ROOT_LOGGER.debugf("ActiveOperation(s) %s have not completed within %d %s", activeRequests.keySet(), timeout, unit);
            }
            return allComplete;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get the executor
     *
     * @return the executor
     */
    protected ExecutorService getExecutor() {
        return executorService;
    }

    /**
     * Get the request handler.
     *
     * @param header the request header
     * @return the request handler
     */
    protected ManagementRequestHandler<?, ?> getRequestHandler(final ManagementRequestHeader header) {
        return getFallbackHandler(header);
    }

    /**
     * Validate whether the request can be handled.
     *
     * @param header the protocol header
     * @return the management request header
     * @throws IOException
     */
    protected ManagementRequestHeader validateRequest(final ManagementProtocolHeader header) throws IOException {
        return (ManagementRequestHeader) header;
    }

    /**
     * Handle a message.
     *
     * @param channel the channel
     * @param input the message
     * @param header the management protocol header
     * @throws IOException
     */
    @Override
    public void handleMessage(final Channel channel, final DataInput input, final ManagementProtocolHeader header) throws IOException {
        final byte type = header.getType();
        if(type == ManagementProtocol.TYPE_RESPONSE) {
            // Handle response to local requests
            final ManagementResponseHeader response =  (ManagementResponseHeader) header;
            final ActiveRequest<?, ?> request = requests.remove(response.getResponseId());
            if(request == null) {
                ProtocolLogger.CONNECTION_LOGGER.noSuchRequest(response.getResponseId(), channel);
                safeWriteErrorResponse(channel, header, ProtocolLogger.ROOT_LOGGER.responseHandlerNotFound(response.getResponseId()));
            } else if(response.getError() != null) {
                request.handleFailed(response);
            } else {
                handleRequest(channel, input, header, request);
            }
        } else {
            // Handle requests (or other messages)
            try {
                final ManagementRequestHeader requestHeader = validateRequest(header);
                final ManagementRequestHandler<?, ?> handler = getRequestHandler(requestHeader);
                if(handler == null) {
                    safeWriteErrorResponse(channel, header, ProtocolLogger.ROOT_LOGGER.responseHandlerNotFound(requestHeader.getBatchId()));
                } else {
                    handleMessage(channel, input, requestHeader, handler);
                }
            } catch (Exception e) {
                safeWriteErrorResponse(channel, header, e);
            }
        }
    }

    /**
     * Execute a request.
     *
     * @param request the request
     * @param channel the channel
     * @param support the request support
     * @return the future result
     */
    protected <T, A> AsyncFuture<T> executeRequest(final ManagementRequest<T, A> request, final Channel channel, final ActiveOperation<T, A> support) {
        assert support != null;
        updateChannelRef(support, channel);
        final Integer requestId = this.requestID.incrementAndGet();
        final ActiveRequest<T, A> ar = new ActiveRequest<T, A>(support, request);
        requests.put(requestId, ar);
        final ManagementRequestHeader header = new ManagementRequestHeader(ManagementProtocol.VERSION, requestId, support.getOperationId(), request.getOperationType());
        final ActiveOperation.ResultHandler<T> resultHandler = support.getResultHandler();
        try {
            request.sendRequest(resultHandler, new ManagementRequestContextImpl<T, A>(support, channel, header, getExecutor()));
        } catch (Exception e) {
            resultHandler.failed(e);
            requests.remove(requestId);
        }
        return support.getResult();
    }

    /**
     * Handle a message.
     *
     * @param channel the channel
     * @param message the message
     * @param header the protocol header
     * @param activeRequest the active request
     */
    protected <T, A> void handleRequest(final Channel channel, final DataInput message, final ManagementProtocolHeader header, ActiveRequest<T, A> activeRequest) {
        handleMessage(channel, message, header, activeRequest.context, activeRequest.handler);
    }

    /**
     * Handle a message.
     *
     * @param channel the channel
     * @param message the message
     * @param header the protocol header
     * @param handler the request handler
     * @throws IOException
     */
    protected <T, A> void handleMessage(final Channel channel, final DataInput message, final ManagementRequestHeader header, ManagementRequestHandler<T, A> handler) throws IOException {
        final ActiveOperation<T, A> support = getActiveOperation(header);
        if(support == null) {
            throw ProtocolLogger.ROOT_LOGGER.responseHandlerNotFound(header.getBatchId());
        }
        handleMessage(channel, message, header, support, handler);
    }

    /**
     * Handle a message.
     *
     * @param channel the channel
     * @param message the message
     * @param header the protocol header
     * @param support the request support
     * @param handler the request handler
     */
    protected <T, A> void handleMessage(final Channel channel, final DataInput message, final ManagementProtocolHeader header,
                                 final ActiveOperation<T, A> support, final ManagementRequestHandler<T, A> handler) {
        assert support != null;
        updateChannelRef(support, channel);
        final ActiveOperation.ResultHandler<T> resultHandler = support.getResultHandler();
        try {
            handler.handleRequest(message, resultHandler,
                    new ManagementRequestContextImpl<T, A>(support, channel, header, getExecutor()));
        } catch (Exception e) {
            resultHandler.failed(e);
            safeWriteErrorResponse(channel, header, e);
        }
    }

    @Override
    public void handleClose(final Channel closed, final IOException exception) {
        handleChannelClosed(closed, exception);
    }

    /**
     * Register an active operation. The operation-id will be generated.
     *
     * @param attachment the shared attachment
     * @return the active operation
     */
    protected <T, A> ActiveOperation<T, A> registerActiveOperation(A attachment) {
        final ActiveOperation.CompletedCallback<T> callback = getDefaultCallback();
        return registerActiveOperation(attachment, callback);
    }

    /**
     * Register an active operation. The operation-id will be generated.
     *
     * @param attachment the shared attachment
     * @param callback the completed callback
     * @return the active operation
     */
    protected <T, A> ActiveOperation<T, A> registerActiveOperation(A attachment, ActiveOperation.CompletedCallback<T> callback) {
        return registerActiveOperation(null, attachment, callback);
    }

    /**
     * Register an active operation with a specific operation id.
     *
     * @param id the operation id
     * @param attachment the shared attachment
     * @return the created active operation
     *
     * @throws java.lang.IllegalStateException if an operation with the same id is already registered
     */
    protected <T, A> ActiveOperation<T, A> registerActiveOperation(final Integer id, A attachment) {
        final ActiveOperation.CompletedCallback<T> callback = getDefaultCallback();
        return registerActiveOperation(id, attachment, callback);
    }

    /**
     * Register an active operation with a specific operation id.
     *
     * @param id the operation id
     * @param attachment the shared attachment
     * @param callback the completed callback
     * @return the created active operation
     *
     * @throws java.lang.IllegalStateException if an operation with the same id is already registered
     */
    protected <T, A> ActiveOperation<T, A> registerActiveOperation(final Integer id, A attachment, ActiveOperation.CompletedCallback<T> callback) {
        lock.lock();
        try {
            // Check that we still allow registration
            // TODO WFCORE-199 distinguish client uses from server uses and limit this check to server uses
            // Using id==null may be one way to do this, but we need to consider ops that involve multiple requests
            // TODO WFCORE-845 consider using an IllegalStateException for this
            //assert ! shutdown;
            final Integer operationId;
            if(id == null) {
                // If we did not get an operationId, create a new one
                operationId = operationIdManager.createBatchId();
            } else {
                // Check that the operationId is not already taken
                if(! operationIdManager.lockBatchId(id)) {
                    throw ProtocolLogger.ROOT_LOGGER.operationIdAlreadyExists(id);
                }
                operationId = id;
            }
            final ActiveOperationImpl<T, A> request = new ActiveOperationImpl<T, A>(operationId, attachment, getCheckedCallback(callback), this);
            final ActiveOperation<?, ?> existing =  activeRequests.putIfAbsent(operationId, request);
            if(existing != null) {
                throw ProtocolLogger.ROOT_LOGGER.operationIdAlreadyExists(operationId);
            }
            ProtocolLogger.ROOT_LOGGER.tracef("Registered active operation %d", operationId);
            activeCount++; // condition.signalAll();
            return request;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get an active operation.
     *
     * @param header the request header
     * @return the active operation, {@code null} if if there is no registered operation
     */
    protected <T, A> ActiveOperation<T, A> getActiveOperation(final ManagementRequestHeader header) {
        return getActiveOperation(header.getBatchId());
    }

    /**
     * Get the active operation.
     *
     * @param id the active operation id
     * @return the active operation, {@code null} if if there is no registered operation
     */
    protected <T, A> ActiveOperation<T, A> getActiveOperation(final Integer id) {
        //noinspection unchecked
        return (ActiveOperation<T, A>) activeRequests.get(id);
    }

    /**
     * Cancel all currently active operations.
     *
     * @return a list of cancelled operations
     */
    protected List<Integer> cancelAllActiveOperations() {
        final List<Integer> operations = new ArrayList<Integer>();
        for(final ActiveOperationImpl<?, ?> activeOperation : activeRequests.values()) {
            activeOperation.asyncCancel(false);
            operations.add(activeOperation.getOperationId());
        }
        return operations;
    }

    /**
     * Remove an active operation.
     *
     * @param id the operation id
     * @return the removed active operation, {@code null} if there was no registered operation
     */
    protected <T, A> ActiveOperation<T, A> removeActiveOperation(Integer id) {
        final ActiveOperation<T, A> removed = removeUnderLock(id);
        if(removed != null) {
            for(final Map.Entry<Integer, ActiveRequest<?, ?>> requestEntry : requests.entrySet()) {
                final ActiveRequest<?, ?> request = requestEntry.getValue();
                if(request.context == removed) {
                    requests.remove(requestEntry.getKey());
                }
            }
        }
        return removed;
    }

    private <T, A> ActiveOperation<T, A> removeUnderLock(final Integer id) {
        lock.lock(); try {
            final ActiveOperation<?, ?> removed = activeRequests.remove(id);
            if(removed != null) {
                ProtocolLogger.ROOT_LOGGER.tracef("Deregistered active operation %d", id);
                activeCount--;
                operationIdManager.freeBatchId(id);
                condition.signalAll();
            }
            //noinspection unchecked
            return (ActiveOperation<T, A>) removed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Safe write error response.
     *
     * @param channel the channel
     * @param header the request header
     * @param error the exception
     */
    protected static void safeWriteErrorResponse(final Channel channel, final ManagementProtocolHeader header, final Throwable error) {
        if(header.getType() == ManagementProtocol.TYPE_REQUEST) {
            try {
                writeErrorResponse(channel, (ManagementRequestHeader) header, error);
            } catch(IOException ioe) {
                ProtocolLogger.ROOT_LOGGER.tracef(ioe, "failed to write error response for %s on channel: %s", header, channel);
            }
        }
    }

    /**
     * Write an error response.
     *
     * @param channel the channel
     * @param header the request
     * @param error the error
     * @throws IOException
     */
    protected static void writeErrorResponse(final Channel channel, final ManagementRequestHeader header, final Throwable error) throws IOException {
        final ManagementResponseHeader response = ManagementResponseHeader.create(header, error);
        final MessageOutputStream output = channel.writeMessage();
        try {
            writeHeader(response, output);
            output.close();
        } finally {
            StreamUtils.safeClose(output);
        }
    }

    /**
     * Write the management protocol header.
     *
     * @param header the mgmt protocol header
     * @param os the output stream
     * @throws IOException
     */
    protected static FlushableDataOutput writeHeader(final ManagementProtocolHeader header, final OutputStream os) throws IOException {
        final FlushableDataOutput output = FlushableDataOutputImpl.create(os);
        header.write(output);
        return output;
    }

    /**
     * Get a fallback handler.
     *
     * @param header the protocol header
     * @return the fallback handler
     */
    protected static <T, A> ManagementRequestHandler<T, A> getFallbackHandler(final ManagementRequestHeader header) {
        return new ManagementRequestHandler<T, A>() {
            @Override
            public void handleRequest(final DataInput input, ActiveOperation.ResultHandler<T> resultHandler, ManagementRequestContext<A> context) throws IOException {
                final Exception error = ProtocolLogger.ROOT_LOGGER.noSuchResponseHandler(Integer.toHexString(header.getRequestId()));
                if(resultHandler.failed(error)) {
                    safeWriteErrorResponse(context.getChannel(), context.getRequestHeader(), error);
                }
            }
        };
    }

    private static void updateChannelRef(final ActiveOperation<?, ?> operation, Channel channel) {
        if (operation instanceof ActiveOperationImpl) {
            final ActiveOperationImpl<?, ?> a = (ActiveOperationImpl) operation;
            a.updateChannelRef(channel);
        }
    }

    private static class ActiveRequest<T, A> {

        private final ActiveOperation<T, A> context;
        private final ManagementResponseHandler<T, A> handler;

        ActiveRequest(ActiveOperation<T, A> context, ManagementResponseHandler<T, A> handler) {
            this.context = context;
            this.handler = handler;
        }

        protected void handleFailed(final ManagementResponseHeader header) {
            handler.handleFailed(header, context.getResultHandler());
        }

    }

}
