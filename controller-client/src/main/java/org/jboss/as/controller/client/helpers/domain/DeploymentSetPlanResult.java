/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

import java.util.Map;
import java.util.UUID;

/**
 * Encapsulates the results of executing a {@link DeploymentSetPlan}.
 *
 * @author Brian Stansberry
 */
public interface DeploymentSetPlanResult {

    /**
     * Gets the unique id of the deployment set plan.
     *
     * @return the id. Will not be <code>null</code>
     */
    UUID getDeploymentSetId();

    /**
     * Gets the deployment set plan that lead to this result.
     * @return the plan. Will not be <code>null</code>
     */
    DeploymentSetPlan getDeploymentSetPlan();

    /**
     * Gets the results for each server group.
     *
     * @return map of server group results, keyed by server group name
     */
    Map<String, ServerGroupDeploymentPlanResult> getServerGroupResults();

    /**
     * Gets the results of the {@link DeploymentAction}s associated with
     * the deployment set plan.
     *
     * @return map of deployment action results, keyed by {@link DeploymentAction#getId() deployment action id}
     */
    Map<UUID, DeploymentActionResult> getDeploymentActionResults();
}
