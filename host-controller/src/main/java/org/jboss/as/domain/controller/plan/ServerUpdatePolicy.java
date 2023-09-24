/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.plan;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;

import java.util.Set;

import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.domain.controller.ServerIdentity;
import org.jboss.dmr.ModelNode;

/**
 * Policy used to determine whether a server can be updated, based on the result
 * of updates made to other servers.
 *
 * @author Brian Stansberry
 */
class ServerUpdatePolicy {
    private final ConcurrentGroupServerUpdatePolicy parent;
    private final String serverGroupName;
    private final Set<ServerIdentity> servers;
    private int successCount;
    private int failureCount;
    private final int maxFailed;

    /**
     * Constructor for normal case where the max number of failures before
     * plan is considered failed comes from the plan.
     *
     * @param parent parent policy
     * @param serverGroupName the name of the server group being updated
     * @param servers servers that are being updated
     * @param maxFailures maximum number of failed servers before the server group should be rolled back
     */
    ServerUpdatePolicy(final ConcurrentGroupServerUpdatePolicy parent,
                            final String serverGroupName,
                            final Set<ServerIdentity> servers,
                            final int maxFailures) {
        assert parent != null : "parent is null";
        assert serverGroupName != null : "serverGroupName is null";
        assert servers != null : "servers is null";

        this.parent = parent;
        this.serverGroupName = serverGroupName;
        this.servers = servers;
        this.maxFailed = maxFailures;
    }

    /**
     * Constructor for the rollback case where failure on one server should
     * not prevent execution on the others.
     *
     * @param parent parent policy
     * @param serverGroupName the name of the server group being updated
     * @param servers servers that are being updated
     */
    ServerUpdatePolicy(final ConcurrentGroupServerUpdatePolicy parent,
                       final String serverGroupName,
                       final Set<ServerIdentity> servers) {
        assert parent != null : "parent is null";
        assert serverGroupName != null : "serverGroupName is null";
        assert servers != null : "servers is null";

        this.parent = parent;
        this.serverGroupName = serverGroupName;
        this.servers = servers;
        this.maxFailed = servers.size();
    }

    /**
     * Gets the name of the server group to which this policy is scoped.
     *
     * @return the name of the server group. Will not be <code>null</code>
     */
    public String getServerGroupName() {
        return serverGroupName;
    }

    /**
     * Gets whether the given server can be updated.
     *
     * @param server the id of the server. Cannot be <code>null</code>
     *
     * @return <code>true</code> if the server can be updated; <code>false</code>
     *          if the update should be cancelled
     *
     * @throws IllegalStateException if this policy is not expecting a request
     *           to update the given server
     */
    public boolean canUpdateServer(ServerIdentity server) {
        if (!serverGroupName.equals(server.getServerGroupName()) || !servers.contains(server)) {
            throw DomainControllerLogger.HOST_CONTROLLER_LOGGER.unknownServer(server);
        }

        if (!parent.canChildProceed())
            return false;

        synchronized (this) {
            return failureCount <= maxFailed;
        }
    }

    /**
     * Records the result of updating a server.
     *
     * @param server  the id of the server. Cannot be <code>null</code>
     * @param response the result of the updates
     */
    public void recordServerResult(ServerIdentity server, ModelNode response) {

        if (!serverGroupName.equals(server.getServerGroupName()) || !servers.contains(server)) {
            throw DomainControllerLogger.HOST_CONTROLLER_LOGGER.unknownServer(server);
        }

        boolean serverFailed = response.has(FAILURE_DESCRIPTION);


        DomainControllerLogger.HOST_CONTROLLER_LOGGER.tracef("Recording server result for '%s': failed = %s",
                server, server);

        synchronized (this) {
            int previousFailed = failureCount;
            if (serverFailed) {
                failureCount++;
            }
            else {
                successCount++;
            }
            if (previousFailed <= maxFailed) {
                if (!serverFailed && (successCount + failureCount) == servers.size()) {
                    // All results are in; notify parent of success
                    parent.recordServerGroupResult(serverGroupName, false);
                }
                else if (serverFailed && failureCount > maxFailed) {
                    parent.recordServerGroupResult(serverGroupName, true);
                }
            }
        }
    }

    /**
     * Gets whether the
     * {@link #recordServerResult(org.jboss.as.domain.controller.ServerIdentity, org.jboss.dmr.ModelNode)} recorded results}
     * constitute a failed server group update per this policy.
     *
     * @return <code>true</code> if the server group update is considered to be a failure;
     *         <code>false</code> otherwise
     */
    public synchronized boolean isFailed() {
        // Here we use successCount instead of failed count, so
        // non-recorded servers are treated as failures
        return (servers.size() - successCount) > maxFailed;
    }
}
