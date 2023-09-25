/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain.impl;

import org.jboss.as.controller.client.logging.ControllerClientLogger;
import org.jboss.as.controller.client.helpers.domain.DeploymentAction;
import org.jboss.as.controller.client.helpers.domain.DeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.domain.ReplaceDeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.domain.ServerGroupDeploymentPlanBuilder;


/**
 * Variant of a {@link DeploymentPlanBuilderImpl} that exposes
 * directives that are only applicable following a <code>replace</code> directive.
 *
 * @author Brian Stansberry
 */
class ReplaceDeploymentPlanBuilderImpl extends DeploymentPlanBuilderImpl implements ReplaceDeploymentPlanBuilder {

    private final DeploymentAction replacementModification;

    ReplaceDeploymentPlanBuilderImpl(DeploymentPlanBuilderImpl existing, DeploymentSetPlanImpl setPlan) {
        super(existing, setPlan);
        this.replacementModification = setPlan.getLastAction();
    }

    @Override
    public ServerGroupDeploymentPlanBuilder toServerGroup(String serverGroupName) {
        return super.toServerGroup(serverGroupName);
    }

    @Override
    public DeploymentPlanBuilder andRemoveUndeployed() {
        DeploymentSetPlanImpl currentSet = getCurrentDeploymentSetPlan();
        if (currentSet.hasServerGroupPlans()) {
            throw ControllerClientLogger.ROOT_LOGGER.cannotAddDeploymentAction();
        }
        DeploymentActionImpl mod = DeploymentActionImpl.getRemoveAction(replacementModification.getReplacedDeploymentUnitUniqueName());
        DeploymentSetPlanImpl newSet = currentSet.addAction(mod);
        return new DeploymentPlanBuilderImpl(this, newSet);
    }
}
