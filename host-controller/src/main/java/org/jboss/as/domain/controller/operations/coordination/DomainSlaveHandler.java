/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations.coordination;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXECUTE_FOR_COORDINATOR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.domain.controller.logging.DomainControllerLogger.HOST_CONTROLLER_LOGGER;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.as.controller.BlockingTimeout;
import org.jboss.as.controller.CurrentOperationIdHolder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.TransformingProxyController;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.operations.OperationAttachments;
import org.jboss.as.controller.remote.ResponseAttachmentInputStreamSupport;
import org.jboss.as.controller.remote.TransactionalProtocolClient;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.dmr.ModelNode;
import org.jboss.threads.AsyncFuture;
import org.jboss.as.controller.operations.DomainOperationTransmuter;

/**
 * Executes the first phase of a two phase operation on one or more remote, slave host controllers.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DomainSlaveHandler implements OperationStepHandler {

    private final MultiphaseOverallContext multiphaseContext;
    private final Map<String, ProxyController> hostProxies;

    public DomainSlaveHandler(final Map<String, ProxyController> hostProxies,
                              final MultiphaseOverallContext domainOperationContext) {
        this.hostProxies = hostProxies;
        this.multiphaseContext = domainOperationContext;
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {

        if (context.hasFailureDescription()) {
            // abort
            context.setRollbackOnly();
            return;
        }

        final BlockingTimeout blockingTimeout = BlockingTimeout.Factory.getDomainBlockingTimeout(context);
        final Set<String> outstanding = new HashSet<String>(hostProxies.keySet());
        final List<TransactionalProtocolClient.PreparedOperation<HostControllerUpdateTask.ProxyOperation>> results = new ArrayList<TransactionalProtocolClient.PreparedOperation<HostControllerUpdateTask.ProxyOperation>>();
        final Map<String, HostControllerUpdateTask.ExecutedHostRequest> finalResults = new HashMap<String, HostControllerUpdateTask.ExecutedHostRequest>();
        final HostControllerUpdateTask.ProxyOperationListener listener = new HostControllerUpdateTask.ProxyOperationListener();
        final Transformers.TransformationInputs transformationInputs = Transformers.TransformationInputs.getOrCreate(context);
        final List<DomainOperationTransmuter> transformers = context.getAttachment(OperationAttachments.SLAVE_SERVER_OPERATION_TRANSMUTERS);
        for (Map.Entry<String, ProxyController> entry : hostProxies.entrySet()) {
            // Create the proxy task
            final String host = entry.getKey();
            final TransformingProxyController proxyController = (TransformingProxyController) entry.getValue();
            ModelNode clonedOp = operation.clone();
            if (transformers != null) {
                for (final DomainOperationTransmuter transformer : transformers) {
                    clonedOp = transformer.transmmute(context, clonedOp);
                }
            }

            // Set the flags for host controller operations
            clonedOp.get(OPERATION_HEADERS, EXECUTE_FOR_COORDINATOR).set(true);
            clonedOp.get(OPERATION_HEADERS, DomainControllerLockIdUtils.DOMAIN_CONTROLLER_LOCK_ID).set(CurrentOperationIdHolder.getCurrentOperationID());
            final HostControllerUpdateTask task = new HostControllerUpdateTask(host, clonedOp, context, proxyController, transformationInputs);
            // Execute the operation on the remote host
            final HostControllerUpdateTask.ExecutedHostRequest finalResult = task.execute(listener);
            multiphaseContext.recordHostRequest(host, finalResult);
            finalResults.put(host, finalResult);
        }

        // Wait for all hosts to reach the prepared state
        boolean interrupted = false;
        boolean completeStepCalled = false;
        try {
            long timeout = 0;
            while (!outstanding.isEmpty()) {
                timeout = blockingTimeout.getDomainBlockingTimeout(false);
                TransactionalProtocolClient.PreparedOperation<HostControllerUpdateTask.ProxyOperation> prepared = null;
                try {
                    prepared = listener.retrievePreparedOperation(timeout, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ie) {
                    interrupted = true;
                }
                if (prepared != null) {
                    final String hostName = prepared.getOperation().getName();
                    if (!outstanding.remove(hostName)) {
                        continue;
                    }
                    final ModelNode preparedResult = prepared.getPreparedResult();
                    HOST_CONTROLLER_LOGGER.tracef("Preliminary result for remote host %s is %s", hostName, preparedResult);
                    // See if we have to reject the result
                    final HostControllerUpdateTask.ExecutedHostRequest request = finalResults.get(hostName);
                    boolean reject = request.rejectOperation(preparedResult);
                    if (reject) {
                        if (HOST_CONTROLLER_LOGGER.isDebugEnabled()) {
                            HOST_CONTROLLER_LOGGER.debugf("Rejecting result for remote host %s is %s", hostName, preparedResult);
                        }
                        final ModelNode failedResult = new ModelNode();
                        failedResult.get(OUTCOME).set(FAILED);
                        failedResult.get(FAILURE_DESCRIPTION).set(request.getFailureDescription());

                        // Record the failed result
                        multiphaseContext.addHostControllerPreparedResult(hostName, failedResult);
                    } else {
                        // Record the prepared result
                        multiphaseContext.addHostControllerPreparedResult(hostName, preparedResult);
                    }
                    results.add(prepared);
                } else {
                    // Either interrupted or timed out.
                    handleMissingHostResponses(finalResults, outstanding, !interrupted, timeout);
                    break;
                }

            }

            if (interrupted) {
                // Interrupt the thread so the OC can learn the operation was interrupted
                // when we call completeStep. The OC will then change the outcome of the
                // op to "cancelled" and prevent further execution of steps. Our
                // finalizeOp method will still be called, via the ResultHandler we pass in.
                Thread.currentThread().interrupt();
            }

            context.completeStep(new OperationContext.ResultHandler() {
                @Override
                public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                    finalizeOp(results, finalResults, false, context, blockingTimeout);
                }
            });

            completeStepCalled = true;

        } finally {
            if (!completeStepCalled) {
                finalizeOp(results, finalResults, interrupted, context, blockingTimeout);
            }
        }
    }

    private void handleMissingHostResponses(Map<String, HostControllerUpdateTask.ExecutedHostRequest> finalResults,
                                            Set<String> outstanding, boolean timedOut, long timeout) {

        // Set rollback only
        multiphaseContext.setFailureReported(true);

        // Cancel all HCs
        if (timedOut) {
            HOST_CONTROLLER_LOGGER.timedOutAwaitingHostPreparedResponses(timeout, outstanding, finalResults.keySet());
        } else {
            HOST_CONTROLLER_LOGGER.interruptedAwaitingHostPreparedResponse(finalResults.keySet());
        }
        for (final HostControllerUpdateTask.ExecutedHostRequest finalResult : finalResults.values()) {
            finalResult.asyncCancel();
        }

        // Record "prepared" responses
        for (String hostName : outstanding) {
            ModelNode failureResponse;
            if (timedOut) {
                failureResponse = getTimeoutResponse(timeout, hostName);
                // Store this locally created response as the final response, since
                // as far as this operation is concerned this slave is non-responsive,
                // what we do here is what rules (i.e. the op failed due to timeout) and
                // we have no idea if any final response from the remote node will make sense
                finalResults.put(hostName, finalResults.get(hostName).toFailedRequest(failureResponse));
            } else {
                failureResponse = getInterruptedResponse(hostName);
                // Here we don't regard this as the final response as we are willing to wait
                // for a final response from the cancelled slave. The slave didn't time out,
                // rather the user cancelled. So we want to report the slave's reaction to that,
                // as that is an aspect of cancellation.
            }
            multiphaseContext.addHostControllerPreparedResult(hostName, failureResponse);
        }
    }

    private void finalizeOp(final List<TransactionalProtocolClient.PreparedOperation<HostControllerUpdateTask.ProxyOperation>> results,
                            final Map<String, HostControllerUpdateTask.ExecutedHostRequest> finalResults,
                            final boolean interrupted, final OperationContext context, final BlockingTimeout blockingTimeout) {

        // If an interrupt occurred, either in our execute method or after it called completeStep,
        // we will be less patient in waiting for final responses, as the user has indicated
        // they want the op ended. Quite likely that is because the op is taking too long.
        boolean interruptThread = Thread.interrupted() || interrupted;
        try {
            // Inform the remote hosts whether to commit or roll back their updates
            // The slaves will then being doing the commit/rollback in parallel
            boolean rollback = multiphaseContext.isCompleteRollback();
            for (final TransactionalProtocolClient.PreparedOperation<HostControllerUpdateTask.ProxyOperation> prepared : results) {

                // Clear any thread interrupted status so we know the commit/rollback message will go out
                interruptThread = Thread.interrupted() || interruptThread;

                if (prepared.isDone()) {
                    continue;
                }
                if (!rollback) {
                    prepared.commit();
                } else {
                    prepared.rollback();
                }
            }
            // Now get the final results from the hosts
            // If we've been interrupted, only wait 50 ms for a final response, otherwise wait the domain blocking timeout
            // Before WFCORE-996 was analyzed, in the interrupted case we would wait 0 ms. 50 ms is a
            // workaround attempt to avoid a race
            int patient = interruptThread ? 50 : blockingTimeout.getDomainBlockingTimeout(false);
            for (final TransactionalProtocolClient.PreparedOperation<HostControllerUpdateTask.ProxyOperation> prepared : results) {
                final String hostName = prepared.getOperation().getName();
                final HostControllerUpdateTask.ExecutedHostRequest request = finalResults.get(hostName);
                final AsyncFuture<OperationResponse> future = prepared.getFinalResult();
                try {
                    final OperationResponse finalResponse = future.get(patient, TimeUnit.MILLISECONDS);
                    final ModelNode transformedResult = request.transformResult(finalResponse.getResponseNode());
                    multiphaseContext.addHostControllerFinalResult(hostName, transformedResult);

                    // Make sure any streams associated with the remote response are properly
                    // integrated with our response
                    ResponseAttachmentInputStreamSupport.handleDomainOperationResponseStreams(context, transformedResult, finalResponse.getInputStreams());

                    HOST_CONTROLLER_LOGGER.tracef("Final result for remote host %s is %s", hostName, finalResponse.getResponseNode());
                    HOST_CONTROLLER_LOGGER.tracef("Transformed result from host %s is %s", hostName, transformedResult);

                } catch (InterruptedException e) {
                    interruptThread = true;
                    future.asyncCancel(true);
                    // We suppressed an interrupt, so don't block indefinitely waiting for other responses;
                    // just grab them if they are already available
                    patient = patient == 0 ? 0 : 50; // if we were already really impatient, we still are
                    HOST_CONTROLLER_LOGGER.interruptedAwaitingFinalResponse(hostName);
                } catch (ExecutionException e) {
                    HOST_CONTROLLER_LOGGER.caughtExceptionAwaitingFinalResponse(e.getCause(), hostName);
                } catch (TimeoutException e) {
                    future.asyncCancel(true);
                    if (interruptThread) {
                        HOST_CONTROLLER_LOGGER.interruptedAwaitingFinalResponse(hostName);
                    } else {
                        HOST_CONTROLLER_LOGGER.timedOutAwaitingFinalResponse(patient, hostName);
                    }
                    // we already waited at least the original 'patient' value since we sent out commit/rollback msgs;
                    // don't need to wait so long any more
                    patient = 0;
                }
            }
        } finally {
            if (interruptThread) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static ModelNode getTimeoutResponse(long timeout, String hostName) {
        String msg = HOST_CONTROLLER_LOGGER.timedOutAwaitingHostPreparedResponse(timeout, hostName);
        final ModelNode response = new ModelNode();
        response.get(OUTCOME).set(FAILED);
        response.get(FAILURE_DESCRIPTION).set(msg);
        return response;
    }

    private static ModelNode getInterruptedResponse(String hostName) {
        String msg = HOST_CONTROLLER_LOGGER.interruptedAwaitingResultFromHost(hostName);
        final ModelNode response = new ModelNode();
        response.get(OUTCOME).set(FAILED);
        response.get(FAILURE_DESCRIPTION).set(msg);
        return response;
    }

}
