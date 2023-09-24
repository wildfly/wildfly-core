/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

/**
 * Variant of a {@link DeploymentPlanBuilder} that exposes
 * directives that signal indicate the completion of the phase of establishing
 * the set of {@link DeploymentAction}s that comprise a {@link DeploymentSetPlan}
 * and the beginning of the phase of specifying how those actions should be
 * applied to server groups.
 *
 * @author Brian Stansberry
 */
public interface DeploymentActionsCompleteBuilder extends DeploymentPlanBuilder {

    /**
     * Indicates that the current set of {@link DeploymentAction deployment actions} comprise
     * a {@link DeploymentSetPlan} and should be applied to a server group.
     * Once this method is invoked, no further actions will be included in the
     * <code>DeploymentSetPlan</code>.
     * <p>
     * Any subsequent <code>add</code>, <code>remove</code>, <code>deploy</code>,
     * <code>replace</code> or <code>undeploy</code> builder operations will
     * signal the start of a new <code>DeploymentSetPlan</code>.
     * </p>
     *
     * @param serverGroupName the name of the server group. Cannot be <code>null</code>
     *
     * @return a builder that can continue building the overall deployment plan
     */
    ServerGroupDeploymentPlanBuilder toServerGroup(String serverGroupName);

}
