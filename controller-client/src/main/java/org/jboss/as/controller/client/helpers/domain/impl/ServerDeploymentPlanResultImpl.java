/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jboss.as.controller.client.helpers.domain.ServerDeploymentPlanResult;
import org.jboss.as.controller.client.helpers.domain.ServerUpdateResult;

/**
 * Default implementation of {@link ServerDeploymentPlanResult}.
 *
 * @author Brian Stansberry
 */
class ServerDeploymentPlanResultImpl implements ServerDeploymentPlanResult {

    private final String serverName;
    private final Map<UUID, ServerUpdateResult> serverResults = new HashMap<UUID, ServerUpdateResult>();

    ServerDeploymentPlanResultImpl(final String serverName) {
        assert serverName != null : "serverName is null";
        this.serverName = serverName;
    }

    @Override
    public Map<UUID, ServerUpdateResult> getDeploymentActionResults() {
        return Collections.unmodifiableMap(serverResults);
    }

    @Override
    public String getServerName() {
        return serverName;
    }

    ServerUpdateResult getServerUpdateResult(UUID actionId) {
        synchronized (serverResults) {
            return serverResults.get(actionId);
        }
    }

    void storeServerUpdateResult(UUID actionId, ServerUpdateResult result) {
        synchronized (serverResults) {
            serverResults.put(actionId, result);
        }
    }

}
