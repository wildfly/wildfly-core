/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.client.helpers.domain.ServerDeploymentPlanResult;
import org.jboss.as.controller.client.helpers.domain.ServerGroupDeploymentPlanResult;


/**
 * Default implementation of {@link ServerGroupDeploymentPlanResult}.
 *
 * @author Brian Stansberry
 */
class ServerGroupDeploymentPlanResultImpl implements ServerGroupDeploymentPlanResult {

    private final String serverGroupName;
    private final Map<String, ServerDeploymentPlanResult> serverResults = new HashMap<String, ServerDeploymentPlanResult>();

    ServerGroupDeploymentPlanResultImpl(final String serverGroupName) {
        assert serverGroupName != null : "serverGroupName is null";
        this.serverGroupName = serverGroupName;
    }

    @Override
    public String getServerGroupName() {
        return serverGroupName;
    }

    @Override
    public Map<String, ServerDeploymentPlanResult> getServerResults() {
        return Collections.unmodifiableMap(serverResults);
    }

    ServerDeploymentPlanResult getServerResult(String server) {
        synchronized (serverResults) {
            return serverResults.get(server);
        }
    }

    void storeServerResult(String server, ServerDeploymentPlanResult result) {
        synchronized (serverResults) {
            serverResults.put(server, result);
        }
    }

}
