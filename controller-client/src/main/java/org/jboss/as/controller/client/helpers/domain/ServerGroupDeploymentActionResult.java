/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

import java.util.Map;


/**
 * Encapsulates the results of particular {@link DeploymentAction} across
 * the servers in a particular server group.
 *
 * @author Brian Stansberry
 */
public interface ServerGroupDeploymentActionResult {

    /**
     * Gets the name of the server group.
     *
     * @return the name. Will not be <code>null</code>
     */
    String getServerGroupName();

    /**
     * Gets the results of the action for each server within the server group.
     *
     * @return map of server results, keyed by server name. Will not be <code>null</code>
     */
    Map<String, ServerUpdateResult> getResultByServer();
}

