/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

import java.util.Map;

/**
 * Encapsulates the results of executing a {@link DeploymentSetPlan} against
 * a particular server group.
 *
 * @see ServerGroupDeploymentPlan
 *
 * @author Brian Stansberry
 */
public interface ServerGroupDeploymentPlanResult {

    /**
     * Gets the name of the server group.
     *
     * @return the name. Will not be <code>null</code>
     */
    String getServerGroupName();

    /**
     * Gets the results for each server within the server group.
     *
     * @return map of server results, keyed by server name. Will not be <code>null</code>
     */
    Map<String, ServerDeploymentPlanResult> getServerResults();
}
