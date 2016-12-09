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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CALLER_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CANCELLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONCURRENT_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_FAILURE_DESCRIPTIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IN_SERIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MAX_FAILED_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MAX_FAILURE_PERCENTAGE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLBACK_ACROSS_GROUPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLING_TO_SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USER;
import static org.jboss.as.domain.controller.logging.DomainControllerLogger.HOST_CONTROLLER_LOGGER;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.as.controller.BlockingTimeout;
import org.jboss.as.controller.CompositeOperationHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.TransformingProxyController;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.remote.ResponseAttachmentInputStreamSupport;
import org.jboss.as.controller.remote.TransactionalProtocolClient;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.domain.controller.plan.RolloutPlanController;
import org.jboss.as.domain.controller.plan.ServerTaskExecutor;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.threads.AsyncFuture;

/**
 * Formulates a rollout plan, invokes the proxies to execute it on the servers.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class DomainRolloutStepHandler implements OperationStepHandler {

    private final MultiphaseOverallContext multiphaseContext;
    private final Map<String, ProxyController> hostProxies;
    private final Map<String, ProxyController> serverProxies;
    private final ExecutorService executorService;
    private final ModelNode serverOperationHeaders;
    private final ModelNode providedRolloutPlan;
    private final boolean trace = HOST_CONTROLLER_LOGGER.isTraceEnabled();

    public DomainRolloutStepHandler(final Map<String, ProxyController> hostProxies,
                                    final Map<String, ProxyController> serverProxies,
                                    final MultiphaseOverallContext multiphaseContext,
                                    final ModelNode rolloutPlan,
                                    final ModelNode serverOperationHeaders,
                                    final ExecutorService executorService) {
        this.hostProxies = hostProxies;
        this.serverProxies = serverProxies;
        this.multiphaseContext = multiphaseContext;
        this.serverOperationHeaders = serverOperationHeaders.clone();
        this.providedRolloutPlan = rolloutPlan;
        this.executorService = executorService;
        //Remove the caller-type=user header
        if (this.serverOperationHeaders.hasDefined(CALLER_TYPE)
                && this.serverOperationHeaders.get(CALLER_TYPE).asString().equals(USER)) {
            this.serverOperationHeaders.remove(CALLER_TYPE);
        }
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {

        if (context.hasFailureDescription()) {
            // abort
            context.setRollbackOnly();
            return;
        }

        // Temporary hack to prevent CompositeOperationHandler throwing away domain failure data
        context.attachIfAbsent(CompositeOperationHandler.DOMAIN_EXECUTION_KEY, Boolean.TRUE);

        final BlockingTimeout blockingTimeout = BlockingTimeout.Factory.getDomainBlockingTimeout(context);

        // Confirm no host failures
        boolean pushToServers = !multiphaseContext.hasHostLevelFailures();
        if (pushToServers) {
            ModelNode ourResult = multiphaseContext.getLocalContext().getLocalResponse();
            if (ourResult.has(FAILURE_DESCRIPTION)) {
                if (trace) {
                    HOST_CONTROLLER_LOGGER.tracef("coordinator failed: %s", ourResult);
                }
                pushToServers = false;
                multiphaseContext.setCompleteRollback(true);
            } else {
                if (trace) {
                    HOST_CONTROLLER_LOGGER.tracef("coordinator succeeded: %s", ourResult);
                }
                for (ModelNode hostResult : multiphaseContext.getHostControllerPreparedResults().values()) {
                    if (hostResult.has(FAILURE_DESCRIPTION)) {
                        if (trace) {
                            HOST_CONTROLLER_LOGGER.tracef("host failed: %s", hostResult);
                        }
                        pushToServers = false;
                        multiphaseContext.setCompleteRollback(true);
                        break;
                    }
                }
            }
        }

        if (pushToServers) {
            // We no longer roll back by default
            multiphaseContext.setCompleteRollback(false);

            final Map<ServerIdentity, ServerTaskExecutor.ExecutedServerRequest> submittedTasks = new HashMap<ServerIdentity, ServerTaskExecutor.ExecutedServerRequest>();
            final List<ServerTaskExecutor.ServerPreparedResponse> preparedResults = new ArrayList<ServerTaskExecutor.ServerPreparedResponse>();
            boolean completeStepCalled = false;
            try {
                pushToServers(context, submittedTasks, preparedResults, blockingTimeout);
                context.completeStep(new OperationContext.ResultHandler() {
                    @Override
                    public void handleResult(OperationContext.ResultAction resultAction, OperationContext context, ModelNode operation) {
                        finalizeOp(context, submittedTasks, preparedResults, blockingTimeout);
                    }
                });

                completeStepCalled = true;
            } finally {
                if (!completeStepCalled) {
                    finalizeOp(context, submittedTasks, preparedResults, blockingTimeout);
                }
            }
        } else {
            // There were failures on hosts, so gather them up and report them
            reportHostFailures(context, operation);
            context.completeStep(OperationContext.ResultHandler.NOOP_RESULT_HANDLER);
        }
    }

    private void finalizeOp(final OperationContext context, final Map<ServerIdentity, ServerTaskExecutor.ExecutedServerRequest> submittedTasks,
                            final List<ServerTaskExecutor.ServerPreparedResponse> preparedResults, final BlockingTimeout blockingTimeout) {

        boolean interrupted = false;
        // Inform the remote hosts whether to commit or roll back their updates
        // Do them all before reading results so the commits/rollbacks can be executed in parallel
        boolean completeRollback = multiphaseContext.isCompleteRollback();
        final String localHostName = multiphaseContext.getLocalHostInfo().getLocalHostName();
        for(final ServerTaskExecutor.ServerPreparedResponse preparedResult : preparedResults) {
            boolean rollback = completeRollback || multiphaseContext.isServerGroupRollback(preparedResult.getServerGroupName());

            // Clear any thread interrupted status to ensure the finalizeTransaction message goes out
            interrupted = Thread.interrupted() || interrupted;

            final ServerIdentity identity = preparedResult.getServerIdentity();
            if (preparedResult.isTimedOut()) {
                HostControllerLogger.ROOT_LOGGER.serverSuspected(identity.getServerName(), identity.getHostName());
            }

            // Require a server reload, in case the operation failed, but the overall state was commit
            if (! preparedResult.finalizeTransaction(! rollback)) {
                try {
                    // Replace the original proxyTask with the requireReloadTask
                    final ModelNode result = preparedResult.getPreparedOperation().getPreparedResult();
                    ProxyController proxy = hostProxies.get(identity.getHostName());
                    if (proxy == null) {
                        if (localHostName.equals(identity.getHostName())) {
                            // Use our server proxies
                            proxy = serverProxies.get(identity.getServerName());
                            if (proxy == null) {
                                if (trace) {
                                    HOST_CONTROLLER_LOGGER.tracef("No proxy for %s", identity);
                                }
                                continue;
                            }
                        }
                    }

                    // This is a failure case, so we assume there are no streams associated with the response
                    // Alternative is to require setting up streams for prepared responses. This would entail:
                    // 1) setting up the response headers in AbstractOperationContext before calling the transaction control
                    // 2) TransactionProtocolClient creating an OperationResponseProxy when it gets the prepared response
                    // 3) Passing that OperationResponse through the various callbacks related to prepared responses
                    // Doable, but I (BES) am not doing it now for such a corner case
                    OperationResponse originalResponse = OperationResponse.Factory.createSimple(result);
                    final Future<OperationResponse> future = executorService.submit(new ServerRequireRestartTask(identity, proxy, originalResponse, blockingTimeout));
                    // replace the existing future
                    submittedTasks.put(identity, new ServerTaskExecutor.ExecutedServerRequest(identity, future));
                } catch (Exception ignore) {
                    // getPreparedResult() won't fail here
                }
            }
        }
        // Now read the final values. This ensures the operations are committed on the remote servers
        // before we expose the servers to further requests

        try {
            // If we've been interrupted, only wait 50 ms for a final response, otherwise wait the domain blocking timeout
            // Before WFCORE-996 was analyzed, in the interrupted case we would wait 0 ms. 50 ms is a
            // workaround attempt to avoid a race
            int patient = interrupted ? 50 : blockingTimeout.getDomainBlockingTimeout(multiphaseContext.getLocalHostInfo().isMasterDomainController());
            for (Map.Entry<ServerIdentity, ServerTaskExecutor.ExecutedServerRequest> entry : submittedTasks.entrySet()) {
                final ServerTaskExecutor.ExecutedServerRequest request = entry.getValue();
                final ServerIdentity sid = entry.getKey();
                final Future<OperationResponse> future = request.getFinalResult();
                try {
                    final OperationResponse finalResponse = future.isCancelled()
                            ? getCancelledResult()
                            : future.get(patient, TimeUnit.MILLISECONDS);

                    final ModelNode untransformedResponse = finalResponse.getResponseNode();
                    HOST_CONTROLLER_LOGGER.tracef("Final response from %s is %s (untransformed)", sid, untransformedResponse);
                    final ModelNode transformedResult = request.transformResult(untransformedResponse);

                    // Make sure any streams associated with the remote response are properly
                    // integrated with our response
                    ResponseAttachmentInputStreamSupport.handleDomainOperationResponseStreams(context, transformedResult, finalResponse.getInputStreams());

                    HOST_CONTROLLER_LOGGER.tracef("Transformed final response from %s is %s", sid, transformedResult);

                    multiphaseContext.addServerResult(sid, transformedResult);
                } catch (InterruptedException e) {
                    cancelPreferAsync(future, true);
                    interrupted = true;
                    // We suppressed an interrupt, so don't block indefinitely waiting for other responses;
                    // just grab them if they are already available
                    patient = patient == 0 ? 0 : 50; // if we were already really impatient, we still are
                    HOST_CONTROLLER_LOGGER.interruptedAwaitingFinalResponse(sid.getServerName(), sid.getHostName());
                } catch (ExecutionException e) {
                    cancelPreferAsync(future, true);
                    HOST_CONTROLLER_LOGGER.caughtExceptionAwaitingFinalResponse(e.getCause(), sid.getServerName(), sid.getHostName());
                } catch (TimeoutException e) {
                    cancelPreferAsync(future, true);
                    if (interrupted) {
                        HOST_CONTROLLER_LOGGER.interruptedAwaitingFinalResponse(sid.getServerName(), sid.getHostName());
                    } else {
                        HOST_CONTROLLER_LOGGER.timedOutAwaitingFinalResponse(patient, sid.getServerName(), sid.getHostName());
                    }
                    // we already waited at least the original 'patient' value since we sent out commit/rollback msgs;
                    // don't need to wait so long any more
                    patient = 0;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void cancelPreferAsync(Future<?> future, boolean mayInterruptIfRunning) {

        if (future instanceof AsyncFuture) { // the normal case
            ((AsyncFuture) future).asyncCancel(mayInterruptIfRunning);
        } else { // the ServerRequireRestartTask case, where we're interrupting a thread executing an op locally
            future.cancel(mayInterruptIfRunning);
        }
    }

    private OperationResponse getCancelledResult() {
        ModelNode cancelled = new ModelNode();
        cancelled.get(OUTCOME).set(CANCELLED);
        return OperationResponse.Factory.createSimple(cancelled);
    }

    private void pushToServers(final OperationContext context, final Map<ServerIdentity, ServerTaskExecutor.ExecutedServerRequest> submittedTasks,
                               final List<ServerTaskExecutor.ServerPreparedResponse> preparedResults, final BlockingTimeout blockingTimeout) throws OperationFailedException {

        final String localHostName = multiphaseContext.getLocalHostInfo().getLocalHostName();
        Map<String, ModelNode> hostResults = new HashMap<String, ModelNode>(multiphaseContext.getHostControllerPreparedResults());
        ModelNode coordinatorOps = multiphaseContext.getLocalContext().getLocalServerOps();
        if (coordinatorOps.isDefined()) {
            // Make a node structure that looks like a response from a remote slave
            ModelNode localNode = new ModelNode();
            localNode.get(RESULT, SERVER_OPERATIONS).set(coordinatorOps);
            hostResults.put(localHostName, localNode);
        }
        Map<String, Map<ServerIdentity, ModelNode>> opsByGroup = getOpsByGroup(hostResults);
        if (opsByGroup.size() > 0) {

            final ModelNode rolloutPlan = getRolloutPlan(this.providedRolloutPlan, opsByGroup);
            if (trace) {
                HOST_CONTROLLER_LOGGER.tracef("Rollout plan is %s", rolloutPlan);
            }

            final Transformers.TransformationInputs transformationInputs = Transformers.TransformationInputs.getOrCreate(context);
            final ServerTaskExecutor taskExecutor = new ServerTaskExecutor(context, submittedTasks, preparedResults) {

                @Override
                protected int execute(TransactionalProtocolClient.TransactionalOperationListener<ServerTaskExecutor.ServerOperation> listener, ServerIdentity server, ModelNode original) throws OperationFailedException {
                    final String hostName = server.getHostName();
                    ProxyController proxy = hostProxies.get(hostName);
                    if (proxy == null) {
                        if (localHostName.equals(hostName)) {
                            // Use our server proxies
                            proxy = serverProxies.get(server.getServerName());
                        }
                        if (proxy == null) {
                            if (trace) {
                                HOST_CONTROLLER_LOGGER.tracef("No proxy for %s", server);
                            }
                            return -1;
                        }
                    }
                    // Transform the server-results
                    final TransformingProxyController remoteProxyController = (TransformingProxyController) proxy;
                    final OperationTransformer.TransformedOperation transformed = multiphaseContext.transformServerOperation(hostName, remoteProxyController, transformationInputs, original);
                    final ModelNode transformedOperation = transformed.getTransformedOperation();
                    final OperationResultTransformer resultTransformer = transformed.getResultTransformer();
                    final TransactionalProtocolClient client = remoteProxyController.getProtocolClient();
                    if (executeOperation(listener, client, server, transformedOperation, resultTransformer)) {
                        return blockingTimeout.getProxyBlockingTimeout(server.toPathAddress(), remoteProxyController);
                    } else {
                        return -1;
                    }
                }
            };
            RolloutPlanController rolloutPlanController = new RolloutPlanController(opsByGroup, rolloutPlan,
                    multiphaseContext, taskExecutor, executorService, blockingTimeout);
            RolloutPlanController.Result planResult = rolloutPlanController.execute();
            if (trace) {
                HOST_CONTROLLER_LOGGER.tracef("Rollout plan result is %s", planResult);
            }
            if (planResult == RolloutPlanController.Result.FAILED ||
                    (planResult == RolloutPlanController.Result.PARTIAL && multiphaseContext.isCompleteRollback())) {
                multiphaseContext.setCompleteRollback(true);
                // AS7-801 -- we need to record a failure description here so the local host change gets aborted
                // Waiting to do it in the DomainFinalResultHandler on the way out is too late
                // Create the result node first so the server results will end up before the failure stuff
                context.getResult();
                context.getFailureDescription().set(DomainControllerLogger.HOST_CONTROLLER_LOGGER.operationFailedOrRolledBack());
                multiphaseContext.setFailureReported(true);
            }
        }
    }

    private Map<String, Map<ServerIdentity, ModelNode>> getOpsByGroup(Map<String, ModelNode> hostResults) {
        Map<String, Map<ServerIdentity, ModelNode>> result = new HashMap<String, Map<ServerIdentity, ModelNode>>();

        for (Map.Entry<String, ModelNode> entry : hostResults.entrySet()) {
            if (trace) {
                HOST_CONTROLLER_LOGGER.tracef("1st phase result from host %s is %s", entry.getKey(), entry.getValue());
            }
            ModelNode hostResult = entry.getValue().get(RESULT);
            if (hostResult.hasDefined(SERVER_OPERATIONS)) {
                String host = entry.getKey();
                for (ModelNode item : hostResult.get(SERVER_OPERATIONS).asList()) {
                    ModelNode op = translateDomainMappedOperation(item.require(OP));
                    for (Property prop : item.require(SERVERS).asPropertyList()) {
                        String group = prop.getValue().asString();
                        Map<ServerIdentity, ModelNode> groupMap = result.get(group);
                        if (groupMap == null) {
                            groupMap = new HashMap<ServerIdentity, ModelNode>();
                            result.put(group, groupMap);
                        }
                        groupMap.put(new ServerIdentity(host, group, prop.getName()), op);
                    }
                }
            }
        }
        return result;
    }

    private ModelNode translateDomainMappedOperation(final ModelNode domainMappedOperation) {
        if (domainMappedOperation.hasDefined(OP)) {
            // Simple op; add headers and return it
            return incorporateServerOperationHeaders(domainMappedOperation);
        }
        ModelNode composite = new ModelNode();
        composite.get(OP).set(COMPOSITE);
        ModelNode steps = composite.get(STEPS).setEmptyList();
        for (Property property : domainMappedOperation.asPropertyList()) {
            steps.add(translateDomainMappedOperation(property.getValue()));
        }
        return incorporateServerOperationHeaders(composite);
    }

    private ModelNode incorporateServerOperationHeaders(ModelNode op) {
        if (serverOperationHeaders.isDefined()) {
            if (op.hasDefined(OPERATION_HEADERS)) {
                // WFCORE-2055 -- preserve any existing headers not declared at the server level
                ModelNode headers = op.get(OPERATION_HEADERS);
                for (Property prop : serverOperationHeaders.asPropertyList()) {
                    headers.get(prop.getName()).set(prop.getValue());
                }
            } else {
                op.get(OPERATION_HEADERS).set(serverOperationHeaders);
            }
        }
        return op;
    }

    private ModelNode getRolloutPlan(ModelNode rolloutPlan, Map<String, Map<ServerIdentity, ModelNode>> opsByGroup) throws OperationFailedException {

        if (rolloutPlan == null || !rolloutPlan.isDefined()) {
            rolloutPlan = getDefaultRolloutPlan(opsByGroup);
        }
        else {
            // Validate that plan covers all groups
            Set<String> found = new HashSet<String>();
            if (rolloutPlan.hasDefined(IN_SERIES)) {
                for (ModelNode series : rolloutPlan.get(IN_SERIES).asList()) {
                    if (series.hasDefined(CONCURRENT_GROUPS)) {
                        for(Property prop : series.get(CONCURRENT_GROUPS).asPropertyList()) {
                            validateServerGroupPlan(found, prop);
                        }
                    }
                    else if (series.hasDefined(SERVER_GROUP)) {
                        Property prop = series.get(SERVER_GROUP).asProperty();
                        validateServerGroupPlan(found, prop);
                    }
                    else {
                        throw new OperationFailedException(DomainControllerLogger.HOST_CONTROLLER_LOGGER.invalidRolloutPlan(series, IN_SERIES));
                    }
                }
            }

            Set<String> groups = new HashSet<String>(opsByGroup.keySet());
            groups.removeAll(found);
            if (!groups.isEmpty()) {
                throw new OperationFailedException(DomainControllerLogger.HOST_CONTROLLER_LOGGER.invalidRolloutPlan(groups));
            }
        }
        return rolloutPlan;
    }

    private void validateServerGroupPlan(Set<String> found, Property prop) throws OperationFailedException {
        if (!found.add(prop.getName())) {
            throw new OperationFailedException(DomainControllerLogger.HOST_CONTROLLER_LOGGER.invalidRolloutPlanGroupAlreadyExists(prop.getName()));
        }
        ModelNode plan = prop.getValue();
        if (plan.hasDefined(MAX_FAILURE_PERCENTAGE)) {
            if (plan.has(MAX_FAILED_SERVERS)) {
                plan.remove(MAX_FAILED_SERVERS);
            }
            int max = plan.get(MAX_FAILURE_PERCENTAGE).asInt();
            if (max < 0 || max > 100) {
                throw new OperationFailedException(DomainControllerLogger.HOST_CONTROLLER_LOGGER.invalidRolloutPlanRange(prop.getName(), MAX_FAILURE_PERCENTAGE, max));
            }
        }
        if (plan.hasDefined(MAX_FAILED_SERVERS)) {
            int max = plan.get(MAX_FAILED_SERVERS).asInt();
            if (max < 0) {
                throw new OperationFailedException(DomainControllerLogger.HOST_CONTROLLER_LOGGER.invalidRolloutPlanLess(prop.getName(), MAX_FAILED_SERVERS, max));
            }
        }
    }

    private ModelNode getDefaultRolloutPlan(Map<String, Map<ServerIdentity, ModelNode>> opsByGroup) {
        ModelNode result = new ModelNode();
        if (opsByGroup.size() > 0) {
            ModelNode groups = result.get(IN_SERIES).add().get(CONCURRENT_GROUPS);

            ModelNode groupPlan = new ModelNode();
            groupPlan.get(ROLLING_TO_SERVERS).set(false);
            groupPlan.get(MAX_FAILED_SERVERS).set(0);

            for (String group : opsByGroup.keySet()) {
                groups.add(group, groupPlan);
            }
            result.get(ROLLBACK_ACROSS_GROUPS).set(true);
        }
        return result;
    }

    private void reportHostFailures(final OperationContext context, final ModelNode operation) {

        final boolean isDomain = isDomainOperation(operation);
        if (!collectDomainFailure(context, isDomain)) {
            collectHostFailures(context, isDomain);
        }
    }

    private boolean collectDomainFailure(OperationContext context, final boolean isDomain) {
        final ModelNode coordinator = multiphaseContext.getLocalContext().getLocalResponse();
        ModelNode domainFailure = null;
        if (isDomain && coordinator.has(FAILURE_DESCRIPTION)) {
            domainFailure = coordinator.hasDefined(FAILURE_DESCRIPTION) ? coordinator.get(FAILURE_DESCRIPTION) : new ModelNode().set(DomainControllerLogger.HOST_CONTROLLER_LOGGER.unexplainedFailure());
        }
        if (domainFailure != null) {
            context.getFailureDescription().get(DOMAIN_FAILURE_DESCRIPTION).set(domainFailure);
            multiphaseContext.setFailureReported(true);
            return true;
        }
        return false;
    }

    private boolean collectHostFailures(final OperationContext context, final boolean isDomain) {
        ModelNode hostFailureResults = null;
        for (Map.Entry<String, ModelNode> entry : multiphaseContext.getHostControllerPreparedResults().entrySet()) {
            ModelNode hostResult = entry.getValue();
            if (hostResult.has(FAILURE_DESCRIPTION)) {
                if (hostFailureResults == null) {
                    hostFailureResults = new ModelNode();
                }
                final ModelNode desc = hostResult.hasDefined(FAILURE_DESCRIPTION) ? hostResult.get(FAILURE_DESCRIPTION) : new ModelNode().set(DomainControllerLogger.HOST_CONTROLLER_LOGGER.unexplainedFailure());
                hostFailureResults.add(entry.getKey(), desc);
            }
        }

        final ModelNode coordinator = multiphaseContext.getLocalContext().getLocalResponse();
        if (!isDomain && coordinator.has(FAILURE_DESCRIPTION)) {
            if (hostFailureResults == null) {
                hostFailureResults = new ModelNode();
            }
            final ModelNode desc = coordinator.hasDefined(FAILURE_DESCRIPTION) ? coordinator.get(FAILURE_DESCRIPTION) : new ModelNode().set(DomainControllerLogger.HOST_CONTROLLER_LOGGER.unexplainedFailure());
            hostFailureResults.add(multiphaseContext.getLocalHostInfo().getLocalHostName(), desc);
        }

        if (hostFailureResults != null) {
            context.getFailureDescription().get(HOST_FAILURE_DESCRIPTIONS).set(hostFailureResults);
            multiphaseContext.setFailureReported(true);
            return true;
        }
        return false;
    }

    private boolean isDomainOperation(final ModelNode operation) {
        final PathAddress address = PathAddress.pathAddress(operation.require(OP_ADDR));
        return address.size() == 0 || !address.getElement(0).getKey().equals(HOST);
    }
}
