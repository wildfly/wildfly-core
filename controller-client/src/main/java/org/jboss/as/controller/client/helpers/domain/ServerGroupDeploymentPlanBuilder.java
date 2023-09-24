/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

/**
 * Variant of a {@link DeploymentPlanBuilder} that exposes
 * directives that are only applicable when controlling how a {@link DeploymentSetPlan}
 * should be applied to one or more server groups.
 *
 * @author Brian Stansberry
 */
public interface ServerGroupDeploymentPlanBuilder extends DeploymentPlanBuilder {

    /**
     * Indicates that  all <code>deploy</code>, <code>undeploy</code> or
     * <code>replace</code> operations associated with the deployment set
     * should be rolled back on all servers in the current server group
     * in case of a failure in any of them.
     *
     * @return a builder that can continue building the overall deployment plan
     */
    RollbackDeploymentPlanBuilder withRollback();

    /**
     * Indicates the deployment actions in the {@link DeploymentSetPlan} should
     * be rolled out to the servers in the server group one server at a time.
     * If this directive is not set the actions may be concurrently applied to
     * servers in the server group.
     *
     * @return a builder that can continue building the overall deployment plan
     */
    ServerGroupDeploymentPlanBuilder rollingToServers();

    /**
     * Indicates that once the deployment actions in the {@link DeploymentSetPlan}
     * are applied to the servers in the current server group, they should then
     * be applied to the servers in the given server group.
     *
     * @param serverGroupName the name of the server group. Cannot be <code>null</code>
     *
     * @return a builder that can continue building the overall deployment plan
     */
    ServerGroupDeploymentPlanBuilder rollingToServerGroup(String serverGroupName);

    /**
     * Indicates that concurrent with applying the deployment actions in the {@link DeploymentSetPlan}
     * to the servers in the current server group, they should also be applied
     * to the servers in the given server group.
     *
     * @param serverGroupName the name of the server group. Cannot be <code>null</code>
     *
     * @return a builder that can continue building the overall deployment plan
     */
    ServerGroupDeploymentPlanBuilder toServerGroup(String serverGroupName);

}
