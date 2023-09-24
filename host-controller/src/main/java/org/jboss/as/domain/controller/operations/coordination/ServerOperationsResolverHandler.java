/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations.coordination;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CALLER_TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_RESULTS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IGNORED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_OPERATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USER;
import static org.jboss.as.domain.controller.logging.DomainControllerLogger.HOST_CONTROLLER_LOGGER;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.CompositeOperationHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.as.host.controller.ServerInventory;
import org.jboss.dmr.ModelNode;

/**
 * Adds to the localResponse the server-level operations needed to effect the given domain/host operation on the
 * servers controlled by this host.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ServerOperationsResolverHandler implements OperationStepHandler {

    public static final String DOMAIN_PUSH_TO_SERVERS = "push-to-servers";
    public static final String OPERATION_NAME = "server-operation-resolver";

    private static final HostControllerExecutionSupport.ServerOperationProvider NO_OP_PROVIDER =
        new HostControllerExecutionSupport.ServerOperationProvider() {
            @Override
            public Map<Set<ServerIdentity>, ModelNode> getServerOperations(ModelNode domainOp, PathAddress address) {

                return Collections.emptyMap();
            }
        };

    private final ServerOperationResolver resolver;
    private final HostControllerExecutionSupport hostControllerExecutionSupport;
    private final PathAddress originalAddress;
    private final ImmutableManagementResourceRegistration originalRegistration;
    private final MultiPhaseLocalContext multiPhaseLocalContext;
    private final ServerInventory serverInventory;

    ServerOperationsResolverHandler(final ServerOperationResolver resolver,
                                    final HostControllerExecutionSupport hostControllerExecutionSupport,
                                    final PathAddress originalAddress,
                                    final ImmutableManagementResourceRegistration originalRegistration,
                                    final MultiPhaseLocalContext multiPhaseLocalContext,
                                    final ServerInventory serverInventory) {
        this.resolver = resolver;
        this.hostControllerExecutionSupport = hostControllerExecutionSupport;
        this.originalAddress = originalAddress;
        this.originalRegistration = originalRegistration;
        this.multiPhaseLocalContext = multiPhaseLocalContext;
        this.serverInventory = serverInventory;
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {

        if (multiPhaseLocalContext.getLocalResponse().has(FAILURE_DESCRIPTION)) {
            // We do not allow failures on the host controllers in a 2-phase op
            context.setRollbackOnly();
        } else {

            // Temporary hack to prevent CompositeOperationHandler throwing away domain failure data
            context.attachIfAbsent(CompositeOperationHandler.DOMAIN_EXECUTION_KEY, Boolean.TRUE);

            // Figure out what server ops are needed to correspond to the domain op we have
            boolean nullDomainOp = hostControllerExecutionSupport.getDomainOperation() == null;
            // Transformed operations might need to simulate certain behavior, so allow read-only operations to be pushed as well
            final boolean pushToServers= operation.hasDefined(OPERATION_HEADERS) && operation.get(OPERATION_HEADERS, DOMAIN_PUSH_TO_SERVERS).asBoolean(false);

            HostControllerExecutionSupport.ServerOperationProvider provider = nullDomainOp
                ? NO_OP_PROVIDER
                : new HostControllerExecutionSupport.ServerOperationProvider() {
                    @Override
                    public Map<Set<ServerIdentity>, ModelNode> getServerOperations(ModelNode domainOp, PathAddress address) {

                        Map<Set<ServerIdentity>, ModelNode> ops = ServerOperationsResolverHandler.this.getServerOperations(context, domainOp, address, pushToServers);
                        for (Map.Entry<Set<ServerIdentity>, ModelNode> entry : ops.entrySet()) {
                            ModelNode op = entry.getValue();
                            //Remove the caller-type=user header
                            if (op.hasDefined(OPERATION_HEADERS, CALLER_TYPE) && op.get(OPERATION_HEADERS, CALLER_TYPE).asString().equals(USER)) {
                                op.get(OPERATION_HEADERS).remove(CALLER_TYPE);
                            }
                        }

                        HOST_CONTROLLER_LOGGER.tracef("Server ops for %s -- %s", domainOp, ops);
                        return ops;
                    }
                };
            Map<ServerIdentity, ModelNode> serverOps = hostControllerExecutionSupport.getServerOps(provider);

            // Format that data and provide it to the coordinator
            ModelNode formattedServerOps = getFormattedServerOps(serverOps);
            if (! serverOps.isEmpty()) {
                final Set<String> serversStarting = new HashSet<>();
                for (Map.Entry<ServerIdentity, ModelNode> serverIdentityModelNodeEntry : serverOps.entrySet()) {
                    String serverName = serverIdentityModelNodeEntry.getKey().getServerName();
                    ServerStatus serverStatus = serverInventory.determineServerStatus(serverName);
                    if (serverStatus == ServerStatus.STARTING) {
                        serversStarting.add(serverName);
                    }
                }
                if (! serversStarting.isEmpty()) {
                    throw HOST_CONTROLLER_LOGGER.serverManagementUnavailableDuringBoot(serversStarting.toString());
                }
            }

            if (multiPhaseLocalContext.isCoordinator()) {
                // We're the coordinator, so just stash the server ops in the multiphase context
                // for use in the rollout plan
                multiPhaseLocalContext.getLocalServerOps().set(formattedServerOps);
                if (HOST_CONTROLLER_LOGGER.isTraceEnabled()) {
                    HOST_CONTROLLER_LOGGER.tracef("%s server ops local response node is %s", getClass().getSimpleName(), formattedServerOps);
                }
            } else {
                // We're not the coordinator, so we need to propagate the server ops
                // to the coordinator via the response we send in the prepare part of Stage.DONE
                // So, change the context result to the special format used for this data
                ModelNode localResult = nullDomainOp ? new ModelNode(IGNORED) : multiPhaseLocalContext.getLocalResponse().get(RESULT);
                ModelNode domainResult = hostControllerExecutionSupport.getFormattedDomainResult(localResult);

                ModelNode contextResult = context.getResult();
                contextResult.setEmptyObject();
                contextResult.get(DOMAIN_RESULTS).set(domainResult);
                contextResult.get(SERVER_OPERATIONS).set(formattedServerOps);


                if (HOST_CONTROLLER_LOGGER.isTraceEnabled()) {
                    HOST_CONTROLLER_LOGGER.tracef("%s server ops remote response node is %s", getClass().getSimpleName(), contextResult);
                }

            }

        }
    }

    private Map<Set<ServerIdentity>, ModelNode> getServerOperations(OperationContext context, ModelNode domainOp, PathAddress domainOpAddress, boolean pushToServers) {
        Map<Set<ServerIdentity>, ModelNode> result = null;
        final PathAddress relativeAddress = domainOpAddress.subAddress(originalAddress.size());
        if(! pushToServers) {
            Set<OperationEntry.Flag> flags = originalRegistration.getOperationFlags(relativeAddress, domainOp.require(OP).asString());
            if (flags != null
                    && flags.contains(OperationEntry.Flag.READ_ONLY)
                    && !flags.contains(OperationEntry.Flag.DOMAIN_PUSH_TO_SERVERS)
                    && !flags.contains(OperationEntry.Flag.RUNTIME_ONLY)) {
                result = Collections.emptyMap();
            }
        }
        if (result == null) {
            result = resolver.getServerOperations(context, domainOp, domainOpAddress);
        }
        return result;
    }

    private ModelNode getFormattedServerOps(Map<ServerIdentity, ModelNode> serverOps) {
        ModelNode serverOpsNode = new ModelNode();

        // Group servers with the same ops together to save bandwidth
        final Map<ModelNode, Set<ServerIdentity>> bundled = new HashMap<ModelNode, Set<ServerIdentity>>();
        for (Map.Entry<ServerIdentity, ModelNode> entry : serverOps.entrySet()) {
            Set<ServerIdentity> idSet = bundled.get(entry.getValue());
            if (idSet == null) {
                idSet = new HashSet<ServerIdentity>();
                bundled.put(entry.getValue(), idSet);
            }
            idSet.add(entry.getKey());
        }
        for (Map.Entry<ModelNode, Set<ServerIdentity>> entry : bundled.entrySet()) {
            ModelNode setNode = serverOpsNode.add();
            ModelNode serverNode = setNode.get("servers");
            serverNode.setEmptyList();
            for (ServerIdentity server : entry.getValue()) {
                serverNode.add(server.getServerName(), server.getServerGroupName());
            }
            setNode.get(OP).set(entry.getKey());
        }
        return serverOpsNode;
    }
}
