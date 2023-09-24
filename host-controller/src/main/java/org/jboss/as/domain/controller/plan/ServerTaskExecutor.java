/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.plan;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.client.MessageSeverity;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.remote.BlockingQueueOperationListener;
import org.jboss.as.controller.remote.TransactionalOperationImpl;
import org.jboss.as.controller.remote.TransactionalProtocolClient;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.threads.AsyncFuture;

/**
 * @author Emanuel Muckenhuber
 */
public abstract class ServerTaskExecutor {

    private final OperationContext context;
    private final Map<ServerIdentity, ExecutedServerRequest> submittedTasks;
    private final List<ServerTaskExecutor.ServerPreparedResponse> preparedResults;

    protected ServerTaskExecutor(OperationContext context, Map<ServerIdentity, ExecutedServerRequest> submittedTasks, List<ServerPreparedResponse> preparedResults) {
        this.context = context;
        this.submittedTasks = submittedTasks;
        this.preparedResults = preparedResults;
    }

    /**
     * Execute
     *
     * @param listener the transactional operation listener
     * @param identity the server identity
     * @param operation the operation
     * @return time to wait in ms for a response from the server, or {@code -1} if the task execution failed locally
     * @throws OperationFailedException
     */
    protected abstract int execute(final TransactionalProtocolClient.TransactionalOperationListener<ServerOperation> listener, final ServerIdentity identity, final ModelNode operation) throws OperationFailedException;

    /**
     * Execute a server task.
     *
     * @param listener the transactional server listener
     * @param task the server task
     * @return time to wait in ms for a response from the server, or {@code -1} if the task execution failed locally
     */
    public int executeTask(final TransactionalProtocolClient.TransactionalOperationListener<ServerOperation> listener, final ServerUpdateTask task) {
        try {
            return execute(listener, task.getServerIdentity(), task.getOperation());
        } catch (OperationFailedException e) {
            // Handle failures operation transformation failures
            final ServerIdentity identity = task.getServerIdentity();
            final ServerOperation serverOperation = new ServerOperation(identity, task.getOperation(), null, null, OperationResultTransformer.ORIGINAL_RESULT);
            final TransactionalProtocolClient.PreparedOperation<ServerOperation> result = BlockingQueueOperationListener.FailedOperation.create(serverOperation, e);
            listener.operationPrepared(result);
            recordExecutedRequest(new ExecutedServerRequest(identity, result.getFinalResult(), OperationResultTransformer.ORIGINAL_RESULT));
            return 1; // 1 ms timeout since there is no reason to wait for the locally stored result
        }

    }

    void cancelTask(ServerIdentity toCancel) {
        ExecutedServerRequest task = submittedTasks.get(toCancel);
        if (task != null) {
            task.asyncCancel();
        }
    }

    /**
     * Execute the operation.
     *
     * @param listener the transactional operation listener
     * @param client the transactional protocol client
     * @param identity the server identity
     * @param operation the operation
     * @param transformer the operation result transformer
     * @return whether the operation was executed
     */
    protected boolean executeOperation(final TransactionalProtocolClient.TransactionalOperationListener<ServerOperation> listener, TransactionalProtocolClient client, final ServerIdentity identity, final ModelNode operation, final OperationResultTransformer transformer) {
        if(client == null) {
            return false;
        }
        final OperationMessageHandler messageHandler = new DelegatingMessageHandler(context);
        final OperationAttachments operationAttachments = new DelegatingOperationAttachments(context);
        final ServerOperation serverOperation = new ServerOperation(identity, operation, messageHandler, operationAttachments, transformer);
        try {
            DomainControllerLogger.HOST_CONTROLLER_LOGGER.tracef("Sending %s to %s", operation, identity);
            final Future<OperationResponse> result = client.execute(listener, serverOperation);
            recordExecutedRequest(new ExecutedServerRequest(identity, result, transformer));
        } catch (IOException e) {
            final TransactionalProtocolClient.PreparedOperation<ServerOperation> result = BlockingQueueOperationListener.FailedOperation.create(serverOperation, e);
            listener.operationPrepared(result);
            recordExecutedRequest(new ExecutedServerRequest(identity, result.getFinalResult(), transformer));
        }
        return true;
    }

    /**
     * Record an executed request.
     *
     * @param task the executed task
     */
    void recordExecutedRequest(final ExecutedServerRequest task) {
        synchronized (submittedTasks) {
            submittedTasks.put(task.getIdentity(), task);
        }
    }

    /**
     * Record a prepare operation.
     *
     * @param preparedOperation the prepared operation
     */
    void recordPreparedOperation(final TransactionalProtocolClient.PreparedOperation<ServerTaskExecutor.ServerOperation> preparedOperation) {
        recordPreparedTask(new ServerTaskExecutor.ServerPreparedResponse(preparedOperation));
    }

    /**
     * Record a prepare operation timeout.
     *
     * @param failedOperation the prepared operation
     */
    void recordOperationPrepareTimeout(final BlockingQueueOperationListener.FailedOperation<ServerOperation> failedOperation) {
        recordPreparedTask(new ServerTaskExecutor.ServerPreparedResponse(failedOperation));
        // Swap out the submitted task so we don't wait for the final result. Use a future the returns
        // prepared response
        ServerIdentity identity = failedOperation.getOperation().getIdentity();
        AsyncFuture<OperationResponse> finalResult = failedOperation.getFinalResult();
        synchronized (submittedTasks) {
            submittedTasks.put(identity, new ServerTaskExecutor.ExecutedServerRequest(identity, finalResult));
        }
    }

    /**
     * Record a prepared operation.
     *
     * @param task the prepared operation
     */
    void recordPreparedTask(ServerTaskExecutor.ServerPreparedResponse task) {
        synchronized (preparedResults) {
            preparedResults.add(task);
        }
    }

    static class ServerOperationListener extends BlockingQueueOperationListener<ServerOperation> {

        @Override
        public void operationPrepared(TransactionalProtocolClient.PreparedOperation<ServerOperation> prepared) {
            super.operationPrepared(prepared);
        }

        @Override
        public void operationComplete(ServerOperation operation, OperationResponse result) {
            super.operationComplete(operation, result);
        }

        @Override
        protected void drainTo(Collection<TransactionalProtocolClient.PreparedOperation<ServerOperation>> preparedOperations) {
            super.drainTo(preparedOperations);
        }

    }

    public static class ServerOperation extends TransactionalOperationImpl {

        private final ServerIdentity identity;
        private final OperationResultTransformer transformer;
        ServerOperation(ServerIdentity identity, ModelNode operation, OperationMessageHandler messageHandler, OperationAttachments attachments, OperationResultTransformer transformer) {
            super(operation, messageHandler, attachments);
            this.identity = identity;
            this.transformer = transformer;
        }

        public ServerIdentity getIdentity() {
            return identity;
        }

        ModelNode transformResult(ModelNode result) {
            return transformer.transformResult(result);
        }

    }

    /**
     * The prepared response.
     */
    public static class ServerPreparedResponse {

        private TransactionalProtocolClient.PreparedOperation<ServerOperation> preparedOperation;
        ServerPreparedResponse(TransactionalProtocolClient.PreparedOperation<ServerOperation> preparedOperation) {
            this.preparedOperation = preparedOperation;
        }

        public TransactionalProtocolClient.PreparedOperation<ServerOperation> getPreparedOperation() {
            return preparedOperation;
        }

        public ServerIdentity getServerIdentity() {
            return preparedOperation.getOperation().getIdentity();
        }

        public String getServerGroupName() {
            return getServerIdentity().getServerGroupName();
        }

        /** Gets whether the response represents a timeout */
        public boolean isTimedOut() {
            return preparedOperation.isTimedOut();
        }

        /**
         * Finalize the transaction. This will return {@code false} in case the local operation failed,
         * but the overall state of the operation is commit=true.
         *
         * @param commit {@code true} to commit, {@code false} to rollback
         * @return whether the local proxy operation result is in sync with the overall operation
         */
        public boolean finalizeTransaction(boolean commit) {
            final boolean failed = preparedOperation.isFailed();
            if(commit && failed) {
                return false;
            }
            if(commit) {
                preparedOperation.commit();
            } else {
                if(!failed) {
                    preparedOperation.rollback();
                }
            }
            return true;
        }

    }

    /**
     * The executed request.
     */
    public static class ExecutedServerRequest implements OperationResultTransformer {

        private final ServerIdentity identity;
        private final Future<OperationResponse> finalResult;
        private final OperationResultTransformer transformer;

        public ExecutedServerRequest(ServerIdentity identity, Future<OperationResponse> finalResult) {
            this(identity, finalResult, OperationResultTransformer.ORIGINAL_RESULT);
        }

        public ExecutedServerRequest(ServerIdentity identity, Future<OperationResponse> finalResult, OperationResultTransformer transformer) {
            this.identity = identity;
            this.finalResult = finalResult;
            this.transformer = transformer;
        }

        public ServerIdentity getIdentity() {
            return identity;
        }

        public Future<OperationResponse> getFinalResult() {
            return finalResult;
        }

        @Override
        public ModelNode transformResult(final ModelNode result) {
            return transformer.transformResult(result);
        }

        private void asyncCancel() {
            if (finalResult instanceof AsyncFuture) {
                ((AsyncFuture) finalResult).asyncCancel(true);
            }
        }

    }

    private static class DelegatingMessageHandler implements OperationMessageHandler {

        private final OperationContext context;

        DelegatingMessageHandler(final OperationContext context) {
            this.context = context;
        }

        @Override
        public void handleReport(MessageSeverity severity, String message) {
            context.report(severity, message);
        }
    }

    private static class DelegatingOperationAttachments implements OperationAttachments {

        private final OperationContext context;
        private DelegatingOperationAttachments(final OperationContext context) {
            this.context = context;
        }

        @Override
        public boolean isAutoCloseStreams() {
            return false;
        }

        @Override
        public List<InputStream> getInputStreams() {
            int count = context.getAttachmentStreamCount();
            List<InputStream> result = new ArrayList<InputStream>(count);
            for (int i = 0; i < count; i++) {
                result.add(context.getAttachmentStream(i));
            }
            return result;
        }

        @Override
        public void close() throws IOException {
            //
        }
    }

}
