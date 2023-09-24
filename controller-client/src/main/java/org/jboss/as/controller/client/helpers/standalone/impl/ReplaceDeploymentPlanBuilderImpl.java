/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.standalone.impl;

import org.jboss.as.controller.client.helpers.standalone.DeploymentAction;
import org.jboss.as.controller.client.helpers.standalone.DeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.standalone.ReplaceDeploymentPlanBuilder;

/**
 * Subclass of {@link DeploymentPlanBuilderImpl} that exposes the additional
 * {@link ReplaceDeploymentPlanBuilder} operations.
 *
 * @author Brian Stansberry
 */
class ReplaceDeploymentPlanBuilderImpl extends DeploymentPlanBuilderImpl implements ReplaceDeploymentPlanBuilder {

    private final DeploymentAction replacementModification;

    ReplaceDeploymentPlanBuilderImpl(DeploymentPlanBuilderImpl existing, DeploymentActionImpl modification) {
        super(existing, modification);
        this.replacementModification = modification;
    }

    /* (non-Javadoc)
     * @see org.jboss.as.deployment.client.api.server.ReplaceDeploymentPlanBuilder#andRemoveUndeployed()
     */
    @Override
    public DeploymentPlanBuilder andRemoveUndeployed() {
        DeploymentActionImpl removeMod = DeploymentActionImpl.getRemoveAction(replacementModification.getReplacedDeploymentUnitUniqueName());
        return new DeploymentPlanBuilderImpl(this, removeMod);
    }
}
