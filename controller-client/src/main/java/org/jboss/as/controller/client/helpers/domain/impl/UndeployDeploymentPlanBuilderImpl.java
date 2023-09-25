/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain.impl;

import org.jboss.as.controller.client.logging.ControllerClientLogger;
import org.jboss.as.controller.client.helpers.domain.DeploymentAction;
import org.jboss.as.controller.client.helpers.domain.RemoveDeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.domain.ServerGroupDeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.domain.UndeployDeploymentPlanBuilder;



/**
 * Variant of a {@link DeploymentPlanBuilderImpl} that exposes
 * directives that are only applicable following an <code>undeploy</code> directive.
 *
 * @author Brian Stansberry
 */
class UndeployDeploymentPlanBuilderImpl extends DeploymentPlanBuilderImpl implements UndeployDeploymentPlanBuilder {

    private final DeploymentAction undeployModification;

    UndeployDeploymentPlanBuilderImpl(DeploymentPlanBuilderImpl existing, DeploymentSetPlanImpl setPlan) {
        super(existing, setPlan);
        DeploymentAction modification = setPlan.getLastAction();
        if (modification.getType() != DeploymentAction.Type.UNDEPLOY) {
            throw ControllerClientLogger.ROOT_LOGGER.invalidActionType(modification.getType());
        }
        this.undeployModification = modification;
    }

    @Override
    public ServerGroupDeploymentPlanBuilder toServerGroup(String serverGroupName) {
        return super.toServerGroup(serverGroupName);
    }

    @Override
    public RemoveDeploymentPlanBuilder andRemoveUndeployed() {
        DeploymentSetPlanImpl currentSet = getCurrentDeploymentSetPlan();
        if (currentSet.hasServerGroupPlans()) {
            throw ControllerClientLogger.ROOT_LOGGER.cannotAddDeploymentActionsAfterStart();
        }
        DeploymentActionImpl mod = DeploymentActionImpl.getRemoveAction(undeployModification.getDeploymentUnitUniqueName());
        DeploymentSetPlanImpl newSet = currentSet.addAction(mod);
        return new RemoveDeploymentPlanBuilderImpl(this, newSet);
    }
}
