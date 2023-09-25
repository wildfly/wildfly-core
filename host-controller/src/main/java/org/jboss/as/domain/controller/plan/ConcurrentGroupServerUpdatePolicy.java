/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.plan;

import java.util.HashSet;
import java.util.Set;

import org.jboss.as.domain.controller.logging.DomainControllerLogger;

/**
 * Policy that controls whether concurrently executing updates to server groups
 * can proceed. Acts a parent to the {@link ServerUpdatePolicy} that controls
 * each concurrently executing server group.
 *
 * @author Brian Stansberry
 */
class ConcurrentGroupServerUpdatePolicy {
    private final ConcurrentGroupServerUpdatePolicy predecessor;
    private final Set<String> groups = new HashSet<String>();
    private int responseCount;
    private boolean failed;

    /**
     * Creates a new ConcurrentGroupServerUpdatePolicy.
     *
     * @param predecessor the policy for a set of server group updates that
     *                    were updated prior to this set. May be <code>null</code>
     *                    if there was no previous set
     * @param groups  the names of the server groups that will be concurrently updated.
     *                    Cannot be <code>null</code>
     */
    ConcurrentGroupServerUpdatePolicy(final ConcurrentGroupServerUpdatePolicy predecessor,
                                      final Set<String> groups) {
        this.predecessor = predecessor;
        this.groups.addAll(groups);
    }

    /**
     * Check from another ConcurrentGroupServerUpdatePolicy whose plans are meant to
     * execute once this policy's plans are successfully completed.
     *
     * @return <code>true</code> if the successor can proceed
     */
    private boolean canSuccessorProceed() {

        if (predecessor != null && !predecessor.canSuccessorProceed()) {
            return false;
        }

        synchronized (this) {
            while (responseCount < groups.size()) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return !failed;
        }
    }

    /**
     * Check from a child {@link ServerUpdatePolicy} as to whether it can
     * proceed.
     *
     * @return <code>true</code> if the child policy can proceed
     */
    public boolean canChildProceed() {
        return predecessor == null || predecessor.canSuccessorProceed();
    }

    /**
     * Records the result of updating a server group.
     *
     * @param serverGroup the server group's name. Cannot be <code>null</code>
     * @param failed <code>true</code> if the server group update failed;
     *               <code>false</code> if it succeeded
     */
    public void recordServerGroupResult(final String serverGroup, final boolean failed) {

        synchronized (this) {
            if (groups.contains(serverGroup)) {
                responseCount++;
                if (failed) {
                    this.failed = true;
                }
                DomainControllerLogger.HOST_CONTROLLER_LOGGER.tracef("Recorded group result for '%s': failed = %s",
                        serverGroup, failed);
                notifyAll();
            }
            else {
                throw DomainControllerLogger.HOST_CONTROLLER_LOGGER.unknownServerGroup(serverGroup);
            }
        }
    }
}
