/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.standalone;

import java.util.UUID;


/**
 * Encapsulates the results of executing a {@link DeploymentPlan}.
 *
 * @author Brian Stansberry
 */
public interface ServerDeploymentPlanResult {

    /**
     * Gets the unique id of the deployment plan.
     *
     * @return the id. Will not be <code>null</code>
     */
    UUID getDeploymentPlanId();

    /**
     * Gets the result of a {@link DeploymentAction} action associated with
     * the deployment set plan.
     *
     * @param deploymentAction the id of the action
     *
     * @return the result
     */
    ServerDeploymentActionResult getDeploymentActionResult(UUID deploymentAction);
}
