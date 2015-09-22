/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.as.controller.CurrentOperationIdHolder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.TransformingProxyController;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.operations.DomainOperationTransformer;
import org.jboss.as.controller.operations.OperationAttachments;
import org.jboss.as.controller.remote.ResponseAttachmentInputStreamSupport;
import org.jboss.as.controller.remote.TransactionalProtocolClient;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.dmr.ModelNode;

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

        final Set<String> outstanding = new HashSet<String>(hostProxies.keySet());
        final List<TransactionalProtocolClient.PreparedOperation<HostControllerUpdateTask.ProxyOperation>> results = new ArrayList<TransactionalProtocolClient.PreparedOperation<HostControllerUpdateTask.ProxyOperation>>();
        final Map<String, HostControllerUpdateTask.ExecutedHostRequest> finalResults = new HashMap<String, HostControllerUpdateTask.ExecutedHostRequest>();
        final HostControllerUpdateTask.ProxyOperationListener listener = new HostControllerUpdateTask.ProxyOperationListener();
        final Transformers.TransformationInputs transformationInputs = Transformers.TransformationInputs.getOrCreate(context);
        for (Map.Entry<String, ProxyController> entry : hostProxies.entrySet()) {
            // Create the proxy task
            final String host = entry.getKey();
            final TransformingProxyController proxyController = (TransformingProxyController) entry.getValue();
            List<DomainOperationTransformer> transformers = context.getAttachment(OperationAttachments.SLAVE_SERVER_OPERATION_TRANSFORMERS);
            ModelNode op = operation;
            if(transformers != null) {
                for(final DomainOperationTransformer transformer : transformers) {
                    op = transformer.transform(context, op);
                    // Set the flag for host controller operations
                    op.get(OPERATION_HEADERS, EXECUTE_FOR_COORDINATOR).set(true);
                }
            }

            ModelNode clonedOp = op.clone();
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
            try {
                while(outstanding.size() > 0) {
                    final TransactionalProtocolClient.PreparedOperation<HostControllerUpdateTask.ProxyOperation> prepared = listener.retrievePreparedOperation();
                    final String hostName = prepared.getOperation().getName();
                    if(! outstanding.remove(hostName)) {
                        continue;
                    }
                    final ModelNode preparedResult = prepared.getPreparedResult();
                    HOST_CONTROLLER_LOGGER.tracef("Preliminary result for remote host %s is %s", hostName, preparedResult);
                    // See if we have to reject the result
                    final HostControllerUpdateTask.ExecutedHostRequest request = finalResults.get(hostName);
                    boolean reject = request.rejectOperation(preparedResult);
                    if(reject) {
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
                }
            } catch (InterruptedException ie) {
                interrupted = true;
                // Set rollback only
                multiphaseContext.setFailureReported(true);
                // Cancel all HCs
                HOST_CONTROLLER_LOGGER.interruptedAwaitingHostPreparedResponse(finalResults.keySet());
                for(final HostControllerUpdateTask.ExecutedHostRequest finalResult : finalResults.values()) {
                    finalResult.asyncCancel();
                }
                // Wait that all hosts are rolled back!?
                for(final Map.Entry<String, HostControllerUpdateTask.ExecutedHostRequest> entry : finalResults.entrySet()) {
                    final String hostName = entry.getKey();
                    try {
                        final HostControllerUpdateTask.ExecutedHostRequest request = entry.getValue();
                        final ModelNode result = request.getFinalResult().get().getResponseNode();
                        final ModelNode transformedResult = request.transformResult(result);
                        multiphaseContext.addHostControllerPreparedResult(hostName, transformedResult);
                    } catch (Exception e) {
                        final ModelNode result = new ModelNode();
                        result.get(OUTCOME).set(FAILED);
                        if (e instanceof InterruptedException) {
                            result.get(FAILURE_DESCRIPTION).set(DomainControllerLogger.HOST_CONTROLLER_LOGGER.interruptedAwaitingResultFromHost(entry.getKey()));
                            interrupted = true;
                        } else {
                            result.get(FAILURE_DESCRIPTION).set(DomainControllerLogger.HOST_CONTROLLER_LOGGER.exceptionAwaitingResultFromHost(entry.getKey(), e.getMessage()));
                        }
                        multiphaseContext.addHostControllerPreparedResult(hostName, result);
                    }
                }
            }

            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            context.completeStep(new OperationContext.ResultHandler() {
                @Override
                public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                    finalizeOp(results, finalResults, false, context);
                }
            });

            completeStepCalled = true;

        } finally {
            if (!completeStepCalled) {
                finalizeOp(results, finalResults, interrupted, context);
            }
        }
    }

    private void finalizeOp(final List<TransactionalProtocolClient.PreparedOperation<HostControllerUpdateTask.ProxyOperation>> results,
                            final Map<String, HostControllerUpdateTask.ExecutedHostRequest> finalResults,
                            final boolean interrupted, final OperationContext context) {
        boolean interruptThread = Thread.interrupted() || interrupted;
        try {
            // Inform the remote hosts whether to commit or roll back their updates
            // Do this in parallel
            boolean rollback = multiphaseContext.isCompleteRollback();
            for(final TransactionalProtocolClient.PreparedOperation<HostControllerUpdateTask.ProxyOperation> prepared : results) {

                // Clear any thread interrupted status so we know the commit/rollback message will go out
                interruptThread = Thread.interrupted() || interruptThread;

                if(prepared.isDone()) {
                    continue;
                }
                if(! rollback) {
                    prepared.commit();
                } else {
                    prepared.rollback();
                }
            }
            // Now get the final results from the hosts
            boolean patient = !interruptThread;
            for(final TransactionalProtocolClient.PreparedOperation<HostControllerUpdateTask.ProxyOperation> prepared : results) {
                final String hostName = prepared.getOperation().getName();
                final HostControllerUpdateTask.ExecutedHostRequest request = finalResults.get(hostName);
                final Future<OperationResponse> future = prepared.getFinalResult();
                try {
                    final OperationResponse finalResponse = patient ? future.get() : future.get(0, TimeUnit.MILLISECONDS);
                    final ModelNode transformedResult = request.transformResult(finalResponse.getResponseNode());
                    multiphaseContext.addHostControllerFinalResult(hostName, transformedResult);

                    // Make sure any streams associated with the remote response are properly
                    // integrated with our response
                    ResponseAttachmentInputStreamSupport.handleDomainOperationResponseStreams(context, transformedResult, finalResponse.getInputStreams());

                    HOST_CONTROLLER_LOGGER.tracef("Final result for remote host %s is %s", hostName, finalResponse.getResponseNode());
                    HOST_CONTROLLER_LOGGER.tracef("Transformed result from host %s is %s", hostName, transformedResult);

                } catch (InterruptedException e) {
                    interruptThread = true;
                    future.cancel(true);
                    // We suppressed an interrupt, so don't block indefinitely waiting for other responses;
                    // just grab them if they are already available
                    patient = false;
                    HOST_CONTROLLER_LOGGER.interruptedAwaitingFinalResponse(hostName);
                } catch (ExecutionException e) {
                    HOST_CONTROLLER_LOGGER.caughtExceptionAwaitingFinalResponse(e.getCause(), hostName);
                } catch (TimeoutException e) {
                    // This only happens if we were interrupted previously, so treat it that way
                    future.cancel(true);
                    HOST_CONTROLLER_LOGGER.interruptedAwaitingFinalResponse(hostName);
                }
            }
        } finally {
            if (interruptThread) {
                Thread.currentThread().interrupt();
            }
        }
    }

}
