/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

/**
 * Variant of a {@link DeploymentPlanBuilder} that exposes
 * directives that are only applicable when controlling how to limit
 * {@link ServerGroupDeploymentPlanBuilder#withRollback() rollbacks} when a
 * {@link DeploymentSetPlan} is applied to a server groups.
 *
 * @author Brian Stansberry
 */
public interface RollbackDeploymentPlanBuilder extends ServerGroupDeploymentPlanBuilder {

    /**
     * Allows the application of the deployment set to fail on the given
     * number of servers before triggering rollback of the plan.
     *
     * @param serverFailures the number of servers. Must be greater than <code>0</code>
     *
     * @return a builder that can continue building the overall deployment plan
     */
    ServerGroupDeploymentPlanBuilder allowFailures(int serverFailures);

    /**
     * Allows the application of the deployment set to fail on the given
     * percentage of servers in the server group before triggering rollback of the plan.
     *
     * @param serverFailurePercentage the percentage of servers. Must be between
     *              <code>1</code> and <code>99</code>
     *
     * @return a builder that can continue building the overall deployment plan
     */
    ServerGroupDeploymentPlanBuilder allowPercentageFailures(int serverFailurePercentage);

}
