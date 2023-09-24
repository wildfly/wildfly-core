/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ABSOLUTE_ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS_CONTROL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESPONSE_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNNING_SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUPS;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.controller.client.MessageSeverity;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.remote.ResponseAttachmentInputStreamSupport;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.dmr.ModelNode;

/**
 * Step handler that uses a proxied {@link ModelController} to execute the step.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ProxyStepHandler implements OperationStepHandler {

    private final ProxyController proxyController;
    private final boolean forServer;

    public ProxyStepHandler(final ProxyController proxyController) {
        this.proxyController = proxyController;
        this.forServer = proxyController.getProxyNodeAddress().getLastElement().getKey().equals(RUNNING_SERVER);
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final BlockingTimeout blockingTimeout = BlockingTimeout.Factory.getProxyBlockingTimeout(context);

        OperationMessageHandler messageHandler = new DelegatingMessageHandler(context);

        final AtomicReference<ModelController.OperationTransaction> txRef = new AtomicReference<ModelController.OperationTransaction>();
        final AtomicReference<ModelNode> preparedResultRef = new AtomicReference<ModelNode>();
        final AtomicReference<OperationResponse> finalResultRef = new AtomicReference<>();
        final ProxyController.ProxyOperationControl proxyControl = new ProxyController.ProxyOperationControl() {

            @Override
            public void operationPrepared(ModelController.OperationTransaction transaction, ModelNode result) {
                txRef.set(transaction);
                preparedResultRef.set(result);
            }

            @Override
            public void operationFailed(ModelNode response) {
                finalResultRef.set(OperationResponse.Factory.createSimple(response));
            }

            @Override
            public void operationCompleted(OperationResponse response) {
                finalResultRef.set(response);
            }
        };
        // Transform the operation if needed
        if(proxyController instanceof TransformingProxyController) {
            final TransformingProxyController transformingProxyController = (TransformingProxyController) proxyController;
            final OperationTransformer.TransformedOperation result = transformingProxyController.transformOperation(context, operation);
            final ModelNode transformedOperation = result.getTransformedOperation();
            final OperationResultTransformer resultTransformer = result.getResultTransformer();
            if(transformedOperation != null) {
                final ProxyController.ProxyOperationControl transformingProxyControl = new ProxyController.ProxyOperationControl() {
                    @Override
                    public void operationFailed(final ModelNode response) {
                        final ModelNode transformed;
                        // Check if we can provide a better error message
                        if(result.rejectOperation(response)) {
                            final ModelNode newResponse = new ModelNode();
                            newResponse.get(OUTCOME).set(FAILED);
                            newResponse.get(FAILURE_DESCRIPTION).set(result.getFailureDescription());
                            transformed = newResponse;
                        } else {
                            transformed = response;
                        }
                        final ModelNode result = resultTransformer.transformResult(transformed);
                        proxyControl.operationFailed(result);
                    }

                    @Override
                    public void operationCompleted(final OperationResponse response) {
                        final ModelNode result = resultTransformer.transformResult(response.getResponseNode());
                        proxyControl.operationCompleted(new OperationResponse() {
                            @Override
                            public ModelNode getResponseNode() {
                                return result;
                            }

                            @Override
                            public List<StreamEntry> getInputStreams() {
                                return response.getInputStreams();
                            }

                            @Override
                            public StreamEntry getInputStream(String uuid) {
                                return response.getInputStream(uuid);
                            }

                            @Override
                            public void close() throws IOException {
                                response.close();
                            }
                        });
                    }

                    @Override
                    public void operationPrepared(ModelController.OperationTransaction transaction, ModelNode response) {
                        proxyControl.operationPrepared(transaction, transformResponse(response));
                    }

                    @Override
                    public void operationPrepared(ModelController.OperationTransaction transaction, ModelNode response,
                                                  OperationContext context1) {
                        proxyControl.operationPrepared(transaction, transformResponse(response), context1);
                    }

                    private ModelNode transformResponse(ModelNode response) {
                        final ModelNode transformed;
                        // Check if we have to reject the operation
                        if(result.rejectOperation(response)) {
                            final ModelNode newResponse = new ModelNode();
                            newResponse.get(OUTCOME).set(FAILED);
                            newResponse.get(FAILURE_DESCRIPTION).set(result.getFailureDescription());
                            transformed = newResponse;
                        } else {
                            transformed = response;
                        }
                        return transformed;
                    }
                };
                proxyController.execute(transformedOperation, messageHandler, transformingProxyControl,
                        new DelegatingOperationAttachments(context), blockingTimeout);
            } else {
                // discard the operation
                final ModelNode transformedResult = resultTransformer.transformResult(new ModelNode());
                if(transformedResult != null) {
                    context.getResult().set(transformedResult);
                }
                context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
                return;
            }
        } else {
            proxyController.execute(operation, messageHandler, proxyControl, new DelegatingOperationAttachments(context),
                    blockingTimeout);
        }
        OperationResponse finalResult = finalResultRef.get();
        if (finalResult != null) {
            // operation failed before it could commit
            ModelNode responseNode = finalResult.getResponseNode();
            ControllerLogger.MGMT_OP_LOGGER.tracef("Remote operation %s failed before commit with response %s", operation, responseNode);
            ModelNode result = responseNode.get(RESULT);
            context.getResult().set(result);
            ModelNode failureDesc = responseNode.get(FAILURE_DESCRIPTION);
            RuntimeException stdFailure = translateFailureDescription(failureDesc);
            if (stdFailure != null) {
                ControllerLogger.MGMT_OP_LOGGER.tracef("Converted failure response to %s", stdFailure);
                throw stdFailure;
            }
            context.getFailureDescription().set(failureDesc);
            if (responseNode.hasDefined(RESPONSE_HEADERS)) {
                context.getResponseHeaders().set(responseNode.get(RESPONSE_HEADERS));
            }
            context.completeStep(OperationContext.RollbackHandler.NOOP_ROLLBACK_HANDLER);
        } else {

            completeRemoteTransaction(context, operation, txRef, preparedResultRef, finalResultRef);

        }
    }

    private void completeRemoteTransaction(final OperationContext context, final ModelNode operation,
                                           final AtomicReference<ModelController.OperationTransaction> txRef,
                                           final AtomicReference<ModelNode> preparedResultRef, final AtomicReference<OperationResponse> finalResultRef) {

        boolean completeStepCalled = false;
        try {

            ModelNode preparedResponse = preparedResultRef.get();
            ModelNode preparedResult = preparedResponse.get(RESULT);
            if (preparedResponse.hasDefined(FAILURE_DESCRIPTION)) {
                context.getFailureDescription().set(preparedResponse.get(FAILURE_DESCRIPTION));
                if (preparedResult.isDefined()) {
                    if (context.getCurrentAddress().isMultiTarget()) {
                        untranslateResultAddress(context.getCurrentAddress(), preparedResult);
                    }
                    context.getResult().set(preparedResult);
                }
            }
            else {
                context.getResult().set(preparedResult);
            }

            context.completeStep(new OperationContext.ResultHandler() {
                @Override
                public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                    boolean txCompleted = false;
                    try {
                        ModelController.OperationTransaction tx = txRef.get();
                        try {
                            if (resultAction == OperationContext.ResultAction.KEEP) {
                                tx.commit();
                            } else {
                                tx.rollback();
                            }
                        } finally {
                            txCompleted = true;
                        }

                        // Get the final result from the proxy and use it to update our response.
                        // Per the ProxyOperationControl contract, this will have been provided via operationCompleted
                        // by the time the call to OperationTransaction.commit/rollback returns

                        OperationResponse finalResponse = finalResultRef.get();
                        if (finalResponse != null) {
                            ModelNode responseNode = finalResponse.getResponseNode();
                            ModelNode finalResult = responseNode.get(RESULT);
                            PathAddress currentAddress = context.getCurrentAddress();
                            if (currentAddress.isMultiTarget()) {
                                untranslateResultAddress(currentAddress, finalResult);
                            }
                            if (responseNode.hasDefined(FAILURE_DESCRIPTION)) {
                                context.getFailureDescription().set(responseNode.get(FAILURE_DESCRIPTION));
                                if (finalResult.isDefined()) {
                                    context.getResult().set(finalResult);
                                }
                            } else {
                                context.getResult().set(finalResult);
                            }
                            if (context.getProcessType().isHostController() && responseNode.has(SERVER_GROUPS)) {
                                context.getServerResults().set(responseNode.get(SERVER_GROUPS));
                            }
                            if (responseNode.hasDefined(RESPONSE_HEADERS)) {
                                context.getResponseHeaders().set(processResponseHeaders(responseNode.get(RESPONSE_HEADERS)));
                            }

                            // Make sure any streams associated with the remote response are properly
                            // integrated with our response
                            ResponseAttachmentInputStreamSupport.handleDomainOperationResponseStreams(context, responseNode, finalResponse.getInputStreams());
                        } else {
                            // This is an error condition
                            ControllerLogger.MGMT_OP_LOGGER.noFinalProxyOutcomeReceived(operation.get(OP),
                                    operation.get(OP_ADDR), proxyController.getProxyNodeAddress().toModelNode());
                        }
                    } finally {
                        // Ensure the remote side gets a transaction outcome if we can't commit/rollback above
                        if (!txCompleted && txRef.get() != null) {
                            txRef.get().rollback();
                        }
                    }
                }
            });

            completeStepCalled = true;

        } finally {
            // Ensure the remote side gets a transaction outcome if we can't call completeStep above
            if (!completeStepCalled && txRef.get() != null) {
                txRef.get().rollback();
            }
        }
    }

    private ModelNode processResponseHeaders(ModelNode responseHeaders) {
        if (!responseHeaders.hasDefined(ACCESS_CONTROL) || !forServer) {
            return responseHeaders;
        } else {
            ModelNode result = responseHeaders.clone();
            for (ModelNode accItem : result.get(ACCESS_CONTROL).asList()) {
                ModelNode itemAddrNode = accItem.get(ABSOLUTE_ADDRESS);
                PathAddress itemAddr = PathAddress.pathAddress(itemAddrNode);
                itemAddrNode.set(proxyController.getProxyNodeAddress().append(itemAddr).toModelNode());
            }
            return result;
        }
    }

    private void untranslateResultAddress(final PathAddress opAddress, final ModelNode result) {
        for (ModelNode m : result.asList()) {
            if (m.hasDefined(OP_ADDR)) {
                PathAddress resultAddr = PathAddress.pathAddress(m.get(OP_ADDR));
                PathAddress untranslated = ProxyOperationAddressTranslator.SERVER.restoreAddress(opAddress, resultAddr);
                m.get(OP_ADDR).set(untranslated.toModelNode());
            }
        }

    }

    private static RuntimeException translateFailureDescription(ModelNode failureDescription) {

        String failureDesc = failureDescription.asString();
        if (failureDesc.startsWith("WFLYCTL0216")) {
            return new org.jboss.as.controller.registry.Resource.NoSuchResourceException(failureDesc);
        }
        else if (failureDesc.startsWith("WFLYCTL0313")) {
            return new UnauthorizedException(failureDesc);
        }
        return null;
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
