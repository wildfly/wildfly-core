/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations.coordination;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_RESULTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IGNORED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.domain.controller.logging.DomainControllerLogger.HOST_CONTROLLER_LOGGER;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.TransformingProxyController;
import org.jboss.as.controller.client.MessageSeverity;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.remote.BlockingQueueOperationListener;
import org.jboss.as.controller.remote.CompletedFuture;
import org.jboss.as.controller.remote.TransactionalOperationImpl;
import org.jboss.as.controller.remote.TransactionalProtocolClient;
import org.jboss.as.controller.transform.OperationRejectionPolicy;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.TransformationTarget;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.threads.AsyncFuture;

/**
 * @author Emanuel Muckenhuber
 */
class HostControllerUpdateTask {

    private final String name;
    private final ModelNode operation;
    private final OperationContext context;
    private final TransformingProxyController proxyController;
    private final Transformers.TransformationInputs transformationInputs;

    public HostControllerUpdateTask(final String name, final ModelNode operation, final OperationContext context,
                                    final TransformingProxyController proxyController,
                                    final Transformers.TransformationInputs transformationInputs) {
        this.name = name;
        this.context = context;
        this.operation = operation;
        this.proxyController = proxyController;
        this.transformationInputs = transformationInputs;
    }

    public ExecutedHostRequest execute(final ProxyOperationListener listener) {

        final TransactionalProtocolClient client = proxyController.getProtocolClient();
        final OperationMessageHandler messageHandler = new DelegatingMessageHandler(context);
        final OperationAttachments operationAttachments = new DelegatingOperationAttachments(context);
        final SubsystemInfoOperationListener subsystemListener = new SubsystemInfoOperationListener(listener, proxyController.getTransformers());
        try {

            final OperationTransformer.TransformedOperation transformationResult = proxyController.transformOperation(transformationInputs, operation);
            final ModelNode transformedOperation = transformationResult.getTransformedOperation();
            final ProxyOperation proxyOperation = new ProxyOperation(name, transformedOperation, messageHandler, operationAttachments);
            try {
                // Make sure we preserve the operation headers like PrepareStepHandler.EXECUTE_FOR_COORDINATOR
                if(transformedOperation != null) {
                    transformedOperation.get(OPERATION_HEADERS).set(operation.get(OPERATION_HEADERS));
                    // If the operation was transformed
                    if (!operation.equals(transformedOperation)) {
                        // push all operations (incl. read-only) to the servers
                        transformedOperation.get(OPERATION_HEADERS, ServerOperationsResolverHandler.DOMAIN_PUSH_TO_SERVERS).set(true);
                        HOST_CONTROLLER_LOGGER.tracef("Sending %s (transformed to %s) to %s", operation, transformedOperation, name);
                    } else {
                        HOST_CONTROLLER_LOGGER.tracef("Sending %s (untransformed) to %s", transformedOperation, name);
                    }
                    final AsyncFuture<OperationResponse> result = client.execute(subsystemListener, proxyOperation);
                    return new ExecutedHostRequest(result, transformationResult);
                } else {
                    // We assume here that if we have a null transformedOperation, it means the operation must be discarded and not be sent to the slave.
                    // The prepared step for this discarded operation will be a SucceededOperation. Later, when the DomainSlaveHandler handler is building up the
                    // final results, it will use transformationResult.getResultTransformer() as the final result for this discarded operation, which makes the
                    // transformed to decide what to do with this discarded operation.
                    HOST_CONTROLLER_LOGGER.tracef("Discard sending %s (transformed to null) for %s", operation, name);
                    final TransactionalProtocolClient.PreparedOperation<ProxyOperation> result = BlockingQueueOperationListener.SucceededOperation.create(proxyOperation);
                    subsystemListener.operationPrepared(result);
                    return new ExecutedHostRequest(result.getFinalResult(), transformationResult);
                }
            } catch (IOException e) {
                // Handle protocol failures
                final TransactionalProtocolClient.PreparedOperation<ProxyOperation> result = BlockingQueueOperationListener.FailedOperation.create(proxyOperation, e);
                subsystemListener.operationPrepared(result);
                return new ExecutedHostRequest(result.getFinalResult(), transformationResult);
            }
        } catch (OperationFailedException e) {
            // Handle transformation failures
            final ProxyOperation proxyOperation = new ProxyOperation(name, operation, messageHandler, operationAttachments);
            final TransactionalProtocolClient.PreparedOperation<ProxyOperation> result = BlockingQueueOperationListener.FailedOperation.create(proxyOperation, e);
            subsystemListener.operationPrepared(result);
            return new ExecutedHostRequest(result.getFinalResult(), OperationResultTransformer.ORIGINAL_RESULT, OperationTransformer.DEFAULT_REJECTION_POLICY);
        }
    }

    static class ProxyOperation extends TransactionalOperationImpl {

        private final String name;
        protected ProxyOperation(final String name, final ModelNode operation, final OperationMessageHandler messageHandler, final OperationAttachments attachments) {
            super(operation, messageHandler, attachments);
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    static class ExecutedHostRequest implements OperationResultTransformer, OperationRejectionPolicy {

        private final AsyncFuture<OperationResponse> futureResult;
        private final OperationResultTransformer resultTransformer;
        private final OperationRejectionPolicy rejectPolicy;

        ExecutedHostRequest(AsyncFuture<OperationResponse> futureResult, OperationResultTransformer resultTransformer, OperationRejectionPolicy rejectPolicy) {
            this.futureResult = futureResult;
            this.resultTransformer = resultTransformer;
            this.rejectPolicy = rejectPolicy;
        }

        ExecutedHostRequest(AsyncFuture<OperationResponse> futureResult, OperationTransformer.TransformedOperation transformedOperation) {
            this(futureResult, transformedOperation, transformedOperation);
        }

        @Override
        public boolean rejectOperation(ModelNode result) {
            // Check the host result for successful operations and see if we have to reject it
            if(result.has(RESULT, DOMAIN_RESULTS)) {
                final ModelNode domainResults = result.get(RESULT, DOMAIN_RESULTS);
                // Don't reject ignored operations
                if(domainResults.getType() == ModelType.STRING && IGNORED.equals(domainResults.asString())) {
                    return false;
                }
                // The format of the prepared operation of the domain coordination step1 is different from a normal operation
                // a user would need to handle, therefore try to fix it up as good as possible
                final ModelNode userOp = new ModelNode();
                userOp.get(OUTCOME).set(SUCCESS);
                userOp.get(RESULT).set(domainResults);
                return rejectPolicy.rejectOperation(userOp);
            } else {
                // This should only handle failed host operations
                return rejectPolicy.rejectOperation(result);
            }
        }

        @Override
        public String getFailureDescription() {
            return rejectPolicy.getFailureDescription();
        }

        @Override
        public ModelNode transformResult(ModelNode result) {
            final boolean reject = rejectOperation(result);
            if(reject) {
                result.get(FAILURE_DESCRIPTION).set(getFailureDescription());
            }
            if(result.has(RESULT, DOMAIN_RESULTS)) {
                final ModelNode domainResults = result.get(RESULT, DOMAIN_RESULTS);
                if(domainResults.getType() == ModelType.STRING && IGNORED.equals(domainResults.asString())) {
                    // Untransformed
                    return result;
                }
                final ModelNode userResult = new ModelNode();
                userResult.get(OUTCOME).set(result.get(OUTCOME));
                userResult.get(RESULT).set(domainResults);
                if(result.hasDefined(FAILURE_DESCRIPTION)) {
                    userResult.get(FAILURE_DESCRIPTION).set(result.get(FAILURE_DESCRIPTION));
                }
                // Transform the result
                final ModelNode transformed = resultTransformer.transformResult(userResult);
                result.get(RESULT, DOMAIN_RESULTS).set(transformed.get(RESULT));
                return result;
            } else {
                return resultTransformer.transformResult(result);
            }
        }

        public void asyncCancel() {
            futureResult.asyncCancel(true);
        }

        ExecutedHostRequest toFailedRequest(ModelNode finalResponse) {
            OperationResponse simpleResponse = OperationResponse.Factory.createSimple(finalResponse);
            return new ExecutedHostRequest(new CompletedFuture<>(simpleResponse), resultTransformer, rejectPolicy);
        }
    }

    /**
     * The transactional operation listener.
     */
    static class ProxyOperationListener extends BlockingQueueOperationListener<ProxyOperation> {
        final boolean trace = HOST_CONTROLLER_LOGGER.isTraceEnabled();

        @Override
        public void operationPrepared(final TransactionalProtocolClient.PreparedOperation<ProxyOperation> prepared) {
            try {
                super.operationPrepared(prepared);
            } finally {
                if (trace) {
                    final ModelNode result = prepared.getPreparedResult();
                    final String hostName = prepared.getOperation().getName();
                    HOST_CONTROLLER_LOGGER.tracef("Received prepared result %s from %s", result, hostName);
                }
            }
        }

        @Override
        public void operationComplete(final ProxyOperation operation, final OperationResponse result) {
            try {
                super.operationComplete(operation, result);
            } finally {
                if (trace) {
                    final String hostName = operation.getName();
                    HOST_CONTROLLER_LOGGER.tracef("Received final result %s from %s", result, hostName);
                }
            }
        }
    }

    /** Checks responses from slaves for subsystem version information. TODO this is pretty hacky */
    private static class SubsystemInfoOperationListener implements TransactionalProtocolClient.TransactionalOperationListener<ProxyOperation> {

        private final ProxyOperationListener delegate;
        private final Transformers transformers;

        private SubsystemInfoOperationListener(ProxyOperationListener delegate, Transformers transformers) {
            this.delegate = delegate;
            this.transformers = transformers;
        }

        @Override
        public void operationPrepared(TransactionalProtocolClient.PreparedOperation<ProxyOperation> prepared) {
            delegate.operationPrepared(prepared);
        }

        @Override
        public void operationFailed(ProxyOperation operation, ModelNode result) {
            delegate.operationFailed(operation, result);
        }

        @Override
        public void operationComplete(ProxyOperation operation, OperationResponse result) {
            try {
                ModelNode responseNode = result.getResponseNode();
                if (responseNode.hasDefined(RESULT, DOMAIN_RESULTS)) {
                    storeSubsystemVersions(operation.getOperation(), responseNode.get(RESULT, DOMAIN_RESULTS));
                }
            } finally {
                delegate.operationComplete(operation, result);
            }
        }

        private void storeSubsystemVersions(ModelNode operation, ModelNode resultNode) {
            PathAddress address = operation.hasDefined(OP_ADDR) ? PathAddress.pathAddress(operation.get(OP_ADDR)) : PathAddress.EMPTY_ADDRESS;
            if (address.size() == 0 && COMPOSITE.equals(operation.get(OP).asString())) {
                // recurse
                List<ModelNode> steps = operation.hasDefined(STEPS) ? operation.get(STEPS).asList() : Collections.<ModelNode>emptyList();
                for (int i = 0; i < steps.size(); i++) {
                    ModelNode stepOp = steps.get(i);
                    String resultID = "step-" + (i+1);
                    if (resultNode.hasDefined(resultID, RESULT)) {
                        storeSubsystemVersions(stepOp, resultNode.get(resultID, RESULT));
                    }
                }
            } else if (address.size() == 1 && ADD.equals(operation.get(OP).asString())
                        && EXTENSION.equals(address.getElement(0).getKey())) {
                // Extract the subsystem info and store it
                TransformationTarget target = transformers.getTarget();
                for (Property p : resultNode.asPropertyList()) {

                    String[] version = p.getValue().asString().split("\\.");
                    int major = Integer.parseInt(version[0]);
                    int minor = Integer.parseInt(version[1]);
                    target.addSubsystemVersion(p.getName(), major, minor);
                    HOST_CONTROLLER_LOGGER.debugf("Registering subsystem %s for host %s with major version [%d] and minor version [%d]",
                            p.getName(), address, major, minor);
                }
                // purge the subsystem version data from the response
                resultNode.set(new ModelNode());
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
