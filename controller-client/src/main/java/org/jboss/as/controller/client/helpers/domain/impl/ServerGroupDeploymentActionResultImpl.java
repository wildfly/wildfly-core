/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.client.helpers.domain.ServerGroupDeploymentActionResult;
import org.jboss.as.controller.client.helpers.domain.ServerUpdateResult;

/**
 * DefaultImplementation of {@link ServerGroupDeploymentActionResult}.
 *
 * @author Brian Stansberry
 */
class ServerGroupDeploymentActionResultImpl implements ServerGroupDeploymentActionResult {

    private final String serverGroupName;
    private final Map<String, ServerUpdateResult> serverResults = new HashMap<String, ServerUpdateResult>();

    ServerGroupDeploymentActionResultImpl(final String serverGroupName) {
        assert serverGroupName != null : "serverGroupName is null";
        this.serverGroupName = serverGroupName;
    }

    @Override
    public Map<String, ServerUpdateResult> getResultByServer() {
        return Collections.unmodifiableMap(serverResults);
    }

    @Override
    public String getServerGroupName() {
        return serverGroupName;
    }

    void storeServerResult(final String serverName, ServerUpdateResult result) {
        serverResults.put(serverName, result);
    }

}
