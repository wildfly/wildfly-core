/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

import java.util.Map;
import java.util.UUID;

/**
 * Encapsulates the results of executing a {@link DeploymentPlan}.
 *
 * @author Brian Stansberry
 */
public interface DeploymentPlanResult {

    /**
     * Gets the unique id of the deployment plan.
     *
     * @return the id. Will not be <code>null</code>
     */
    UUID getId();

    /**
     * Gets the deployment plan that lead to this result.
     *
     * @return the deployment plan. Will not be {@code null}
     */
    DeploymentPlan getDeploymentPlan();

    /**
     * Gets whether the deployment plan was valid. If {@code false} see
     * {@link #getInvalidDeploymentPlanException()} to for more information on
     * how the plan was invalid.
     *
     * @return <code>true</code> if the plan was valid; <code>false</code> otherwise
     */
    boolean isValid();

    /**
     * Gets the exception describing the problem with a deployment plan that
     * is not {@link #isValid() valid}.
     *
     * @return the exception or {@code null} if the plan is valid
     */
    InvalidDeploymentPlanException getInvalidDeploymentPlanException();

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
