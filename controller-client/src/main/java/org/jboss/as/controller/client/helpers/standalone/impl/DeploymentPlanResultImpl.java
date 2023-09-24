/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.standalone.impl;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentActionResult;
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentPlanResult;
import org.wildfly.common.Assert;

/**
 * Default implementation of  {@link ServerDeploymentPlanResult}.
 *
 * @author Brian Stansberry
 */
public class DeploymentPlanResultImpl implements ServerDeploymentPlanResult, Serializable {

    private static final long serialVersionUID = -2473360314683299361L;

    private final Map<UUID, ServerDeploymentActionResult> actionResults;
    private final UUID planId;

    public DeploymentPlanResultImpl(UUID planId, Map<UUID, ServerDeploymentActionResult> actionResults) {
        Assert.checkNotNullParam("planId", planId);
        Assert.checkNotNullParam("actionResults", actionResults);
        this.planId = planId;
        this.actionResults = actionResults;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.deployment.client.api.server.DeploymentPlanResult#getDeploymentActionResult(java.util.UUID)
     */
    @Override
    public ServerDeploymentActionResult getDeploymentActionResult(UUID deploymentAction) {
        return actionResults.get(deploymentAction);
    }

    /* (non-Javadoc)
     * @see org.jboss.as.deployment.client.api.server.DeploymentPlanResult#getDeploymentPlanId()
     */
    @Override
    public UUID getDeploymentPlanId() {
        return planId;
    }

}
