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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_OPERATIONS;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.TransformingProxyController;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.domain.controller.LocalHostControllerInfo;
import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * Stores overall contextual information for a multi-phase operation executing on the domain.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public final class MultiphaseOverallContext {

    private final LocalHostControllerInfo localHostInfo;
    private final MultiPhaseLocalContext localContext = new MultiPhaseLocalContext(true);
    private final ConcurrentMap<String, ModelNode> hostControllerPreparedResults = new ConcurrentHashMap<String, ModelNode>();
    private final ConcurrentMap<String, ModelNode> hostControllerFinalResults = new ConcurrentHashMap<String, ModelNode>();
    private final ConcurrentMap<ServerIdentity, ModelNode> serverResults = new ConcurrentHashMap<ServerIdentity, ModelNode>();
    private final ConcurrentMap<String, HostControllerUpdateTask.ExecutedHostRequest> finalResultFutures = new ConcurrentHashMap<String, HostControllerUpdateTask.ExecutedHostRequest>();

    private final Map<String, Boolean> serverGroupStatuses = new ConcurrentHashMap<String, Boolean>();
    private volatile boolean completeRollback = true;
    private volatile boolean failureReported;

    MultiphaseOverallContext(final LocalHostControllerInfo localHostInfo) {
        this.localHostInfo = localHostInfo;
    }

    LocalHostControllerInfo getLocalHostInfo() {
        return localHostInfo;
    }

    MultiPhaseLocalContext getLocalContext() {
        return localContext;
    }

    Map<String, ModelNode> getHostControllerPreparedResults() {
        return new HashMap<String, ModelNode>(hostControllerPreparedResults);
    }

    void addHostControllerPreparedResult(String hostId, ModelNode hostResult) {
        hostControllerPreparedResults.put(hostId, hostResult);
    }

    Map<String, ModelNode> getHostControllerFinalResults() {
        return new HashMap<String, ModelNode>(hostControllerFinalResults);
    }

    void addHostControllerFinalResult(String hostId, ModelNode hostResult) {
        hostControllerFinalResults.put(hostId, hostResult);
    }

    Map<ServerIdentity, ModelNode> getServerResults() {
        return new HashMap<ServerIdentity, ModelNode>(serverResults);
    }

    void addServerResult(ServerIdentity serverId, ModelNode serverResult) {
        serverResults.put(serverId, serverResult);
    }

    boolean isCompleteRollback() {
        return completeRollback;
    }

    public void setCompleteRollback(boolean completeRollback) {
        this.completeRollback = completeRollback;
    }

    boolean isServerGroupRollback(String serverGroup) {
        Boolean ok = serverGroupStatuses.get(serverGroup);
        return ok == null || ok.booleanValue();
    }

    public void setServerGroupRollback(String serverGroup, boolean rollback) {
        serverGroupStatuses.put(serverGroup, rollback);
    }

    public boolean hasHostLevelFailures() {
        ModelNode coordinatorResult = localContext.getLocalResponse();
        boolean domainFailed = coordinatorResult.isDefined() && coordinatorResult.has(FAILURE_DESCRIPTION);
        if (domainFailed) {
            return true;
        }
        for (ModelNode hostResult : hostControllerPreparedResults.values()) {
            if (hostResult.has(FAILURE_DESCRIPTION)) {
                return true;
            }
        }
        return false;
    }

    public boolean isFailureReported() {
        return failureReported;
    }

    public void setFailureReported(boolean failureReported) {
        this.failureReported = failureReported;
    }

    public ModelNode getServerResult(String hostName, String serverName, String... stepLabels) {
        ModelNode result;
        ServerIdentity id = new ServerIdentity(hostName, null, serverName);
        ModelNode serverResult = getServerResults().get(id);
        if (serverResult == null) {
            return null;
        }
        serverResult = serverResult.clone();
        if (stepLabels.length == 0) {
            result = serverResult;
        } else {
            result = new ModelNode();
            ModelNode hostResults;
            if (hostName.equals(localHostInfo.getLocalHostName())) {
                hostResults = new ModelNode();
                hostResults.get(RESULT, SERVER_OPERATIONS).set(localContext.getLocalServerOps());
            } else {
                hostResults = hostControllerPreparedResults.get(hostName);
            }
            String[] translatedSteps = getTranslatedSteps(serverName, hostResults, stepLabels);
            if (translatedSteps != null && serverResult.hasDefined(translatedSteps)) {
                if (DomainControllerLogger.HOST_CONTROLLER_LOGGER.isTraceEnabled()) {
                    DomainControllerLogger.HOST_CONTROLLER_LOGGER.tracef("Translated steps for %s/%s[%s] are %s",
                            hostName, serverName, Arrays.asList(stepLabels), Arrays.asList(translatedSteps));
                }
                result.set(serverResult.get(translatedSteps));
            }
        }
        return result;
    }

    /*
     * Transform an operation for a server. This will also delegate to the host-controller result-transformer.
     */
    public OperationTransformer.TransformedOperation transformServerOperation(final String hostName, final TransformingProxyController remoteProxyController,
                                                                              final Transformers.TransformationInputs transformationInputs,
                                                                              final ModelNode original) throws OperationFailedException {
        final OperationTransformer.TransformedOperation transformed = remoteProxyController.transformOperation(transformationInputs, original);
        final HostControllerUpdateTask.ExecutedHostRequest hostRequest = finalResultFutures.get(hostName);
        if(hostRequest == null) {
            // in case it's local hosts-controller
            return transformed;
        }
        return new OperationTransformer.TransformedOperation(transformed.getTransformedOperation(), new OperationResultTransformer() {
            @Override
            public ModelNode transformResult(ModelNode result) {
                final ModelNode step1 = transformed.transformResult(result);
                return hostRequest.transformResult(step1);
            }
        });
    }

    protected void recordHostRequest(final String hostName, final HostControllerUpdateTask.ExecutedHostRequest request) {
        finalResultFutures.put(hostName, request);
    }

    private String[] getTranslatedSteps(String serverName, ModelNode hostResults, String[] stepLabels) {
        String[] result = null;
        ModelNode domainMappedOp = getDomainMappedOperation(serverName, hostResults);
        if (domainMappedOp != null) {
            result = new String[stepLabels.length * 2];
            ModelNode level = domainMappedOp;
            for (int i = 0; i < stepLabels.length; i++) {
                String translated = getTranslatedStepIndex(stepLabels[i], level);
                if (translated == null) {
                    return null;
                }
                result[i * 2] = RESULT;
                result[(i * 2) + 1] = translated;
                level = level.get(stepLabels[i]);
            }
        }
        return result;
    }

    private String getTranslatedStepIndex(String stepLabel, ModelNode level) {
        int i = 1;
        for (String key : level.keys()) {
            if (stepLabel.equals(key)) {
                return "step-" + i;
            }
            i++;
        }
        return null;
    }

    private ModelNode getDomainMappedOperation(String serverName, ModelNode hostResults) {
        for (ModelNode set : hostResults.get(RESULT, SERVER_OPERATIONS).asList()) {
            for (Property prop : set.get(SERVERS).asPropertyList()) {
                if (prop.getName().equals(serverName)) {
                    return set.get(OP);
                }
            }
        }
        return null;
    }
}
