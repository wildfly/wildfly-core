/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations.coordination;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_UUID;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXECUTE_FOR_COORDINATOR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLAN;
import static org.jboss.as.domain.controller.logging.DomainControllerLogger.HOST_CONTROLLER_LOGGER;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.as.controller.AccessAuditContext;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * Coordinates the overall execution of an operation on behalf of the domain.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class OperationCoordinatorStepHandler {

    private final LocalHostControllerInfo localHostControllerInfo;
    private final Map<String, ProxyController> hostProxies;
    private final Map<String, ProxyController> serverProxies;
    private final OperationSlaveStepHandler localSlaveHandler;
    private volatile ExecutorService executorService;

    OperationCoordinatorStepHandler(final LocalHostControllerInfo localHostControllerInfo,
                                    final Map<String, ProxyController> hostProxies,
                                    final Map<String, ProxyController> serverProxies,
                                    final OperationSlaveStepHandler localSlaveHandler) {
        this.localHostControllerInfo = localHostControllerInfo;
        this.hostProxies = hostProxies;
        this.serverProxies = serverProxies;
        this.localSlaveHandler = localSlaveHandler;
    }

    void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        // Determine routing
        OperationRouting routing = OperationRouting.determineRouting(context, operation, localHostControllerInfo, hostProxies.keySet());

        if (!localHostControllerInfo.isMasterDomainController() && !routing.isLocalOnly(localHostControllerInfo.getLocalHostName())) {
            // We cannot handle this ourselves
            routeToMasterDomainController(context, operation);
        }
        else if (routing.getSingleHost() != null && !localHostControllerInfo.getLocalHostName().equals(routing.getSingleHost())) {
            if (HOST_CONTROLLER_LOGGER.isTraceEnabled()) {
                HOST_CONTROLLER_LOGGER.trace("Remote single host");
            }
            // This host is the master, but this op is addressed specifically to another host.
            // This is possibly a two step operation, but it's not coordinated by this host.
            // Execute direct (which will proxy the request to the intended HC) and let the remote HC coordinate
            // any two step process (if there is one)
            configureDomainUUID(operation);
            // See if this is a composite; if so use the two step path to avoid breaking it locally into multiple
            // steps that get invoked piecemeal on the target host
            if (COMPOSITE.equals(operation.get(OP).asString()) && PathAddress.pathAddress(operation.get(OP_ADDR)).size() == 0) {
                assert !routing.isMultiphase();
                executeTwoPhaseOperation(context, operation, routing);
            } else {
                executeDirect(context, operation, false); // don't need to check private as we are just going to forward this
            }
        }
        else if (!routing.isMultiphase()) {
            // It's a domain or host level op (probably a read) that does not require bringing in other hosts or servers
            executeDirect(context, operation, true);
        }
        else {
            // Else we are responsible for coordinating a two-phase op
            // -- domain level op: apply to HostController models across domain and then push to servers
            // -- host level op: apply to our model  and then push to servers
            assert routing.isMultiphase();
            executeTwoPhaseOperation(context, operation, routing);
        }
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    private ExecutorService getExecutorService() {
        return executorService == null ? Executors.newSingleThreadExecutor() : executorService;
    }

    private void routeToMasterDomainController(OperationContext context, ModelNode operation) {
        // Per discussion on 2011/03/07, routing requests from a slave to the
        // master may overly complicate the security infrastructure. Therefore,
        // the ability to do this is being disabled until it's clear that it's
        // not a problem
        context.getFailureDescription().set(DomainControllerLogger.HOST_CONTROLLER_LOGGER.masterDomainControllerOnlyOperation(operation.get(OP).asString(), PathAddress.pathAddress(operation.get(OP_ADDR))));
    }

    /**
     * Directly handles the op in the standard way the default prepare step handler would
     * @param context the operation execution context
     * @param operation the operation
     * @param checkPrivate {@code true} if a check should be made for a direct user call to a private operation
     * @throws OperationFailedException if there is no handler registered for the operation
     */
    private void executeDirect(OperationContext context, ModelNode operation, boolean checkPrivate) throws OperationFailedException {
        if (HOST_CONTROLLER_LOGGER.isTraceEnabled()) {
            HOST_CONTROLLER_LOGGER.tracef("%s executing direct", getClass().getSimpleName());
        }
        PrepareStepHandler.executeDirectOperation(context, operation, checkPrivate);
    }

    private void executeTwoPhaseOperation(OperationContext context, ModelNode operation, OperationRouting routing) throws OperationFailedException {

        HOST_CONTROLLER_LOGGER.trace("Executing two-phase");

        configureDomainUUID(operation);

        MultiphaseOverallContext overallContext = new MultiphaseOverallContext(localHostControllerInfo);

        // Get a copy of the headers for use on the servers so they don't get disrupted by any handlers
        // Also get a copy of the rollout plan. Remove it from the headers as no one needs it but us
        final ModelNode operationHeaders = operation.get(OPERATION_HEADERS);
        final ModelNode rolloutPlan = operationHeaders.has(ROLLOUT_PLAN)
                ? operation.get(OPERATION_HEADERS).remove(ROLLOUT_PLAN) : new ModelNode();

        // Create the op we'll ask the HCs to execute
        final ModelNode slaveOp = operation.clone();
        slaveOp.get(OPERATION_HEADERS, EXECUTE_FOR_COORDINATOR).set(true);
        slaveOp.protect();

        HostControllerExecutionSupport localHCES = null;

        // If necessary, execute locally first. This gets all of the Stage.MODEL, Stage.RUNTIME, Stage.VERIFY
        // steps registered. A failure in those will prevent the rest of the steps below executing
        String localHostName = localHostControllerInfo.getLocalHostName();
        if (routing.isLocalCallNeeded(localHostName)) {
            localHCES = localSlaveHandler.addSteps(context, slaveOp.clone(), overallContext.getLocalContext());
        }

        // Add a step that on the way out fixes up the result/failure description. On the way in it does nothing.
        // We set the 'addFirst' param to 'true' so this is placed *before* any steps localSlaveHandler just added
        context.addStep(new DomainFinalResultHandler(overallContext, localHCES), OperationContext.Stage.MODEL, true);

        if (localHostControllerInfo.isMasterDomainController()) {

            // Add steps to invoke on the HC for each relevant slave
            Set<String> remoteHosts = new HashSet<String>(routing.getHosts());
            boolean global = remoteHosts.isEmpty();
            remoteHosts.remove(localHostName);

            if (!remoteHosts.isEmpty() || global) {

                if (routing.isMultiphase()) {
                    // Lock the controller to ensure there are no topology changes mid-op.
                    // This assumes registering/unregistering a remote proxy will involve an op and hence will block
                    //
                    // Notes non-multiphase case
                    // If the routing is non-multiphase we do not acquire the write lock here:
                    // - We don't worry about serverProxies, since there won't any results affecting the servers managed by this host.
                    // - The worst case here is if, in the middle of the operation, the target host is removed. In this case and if
                    // a host is removed (user concurrently shutdown it or its process crashes), this operation could fail, however,
                    // it is considered an expected failure.
                    context.acquireControllerLock();
                }

                if (global) {
                    remoteHosts.addAll(hostProxies.keySet());
                }

                Map<String, ProxyController> remoteProxies = new HashMap<String, ProxyController>();
                for (String host : remoteHosts) {
                    ProxyController proxy = hostProxies.get(host);
                    if (proxy != null) {
                        remoteProxies.put(host, proxy);
                    } else if (!global) {
                        throw DomainControllerLogger.HOST_CONTROLLER_LOGGER.invalidOperationTargetHost(host);
                    }
                }

                context.addStep(slaveOp.clone(), new DomainSlaveHandler(remoteProxies, overallContext), OperationContext.Stage.DOMAIN);
            }
        }

        // Finally, the step to formulate and execute the 2nd phase rollout plan
        context.addStep(new DomainRolloutStepHandler(hostProxies, serverProxies, overallContext, rolloutPlan, operationHeaders, getExecutorService()), OperationContext.Stage.DOMAIN);
    }

    static void configureDomainUUID(ModelNode operation) {
        if (!operation.hasDefined(OPERATION_HEADERS) || !operation.get(OPERATION_HEADERS).hasDefined(DOMAIN_UUID)) {
            String domainUUID = UUID.randomUUID().toString();
            operation.get(OPERATION_HEADERS, DOMAIN_UUID).set(domainUUID);
            AccessAuditContext accessContext = SecurityActions.currentAccessAuditContext();
            if (accessContext != null) {
                accessContext.setDomainUuid(domainUUID);
            }
        }
    }

}
