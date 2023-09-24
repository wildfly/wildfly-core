/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain.impl;

import org.jboss.as.controller.client.logging.ControllerClientLogger;
import org.jboss.as.controller.client.helpers.domain.RollbackDeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.domain.ServerGroupDeploymentPlan;
import org.jboss.as.controller.client.helpers.domain.ServerGroupDeploymentPlanBuilder;


/**
 * Implementation of {@link RollbackDeploymentPlanBuilder}.
 *
 * @author Brian Stansberry
 */
class RollbackDeploymentPlanBuilderImpl extends ServerGroupDeploymentPlanBuilderImpl implements RollbackDeploymentPlanBuilder {

    RollbackDeploymentPlanBuilderImpl(DeploymentPlanBuilderImpl existing, DeploymentSetPlanImpl setPlan) {
        super(existing, setPlan);
    }

    @Override
    public ServerGroupDeploymentPlanBuilder allowFailures(int serverFailures) {
        DeploymentSetPlanImpl setPlan = getCurrentDeploymentSetPlan();
        ServerGroupDeploymentPlan groupPlan = setPlan.getLatestServerGroupDeploymentPlan();
        if (groupPlan == null) {
            throw ControllerClientLogger.ROOT_LOGGER.notConfigured(ServerGroupDeploymentPlan.class.getSimpleName());
        }
        groupPlan = groupPlan.createAllowFailures(serverFailures);
        setPlan = setPlan.storeServerGroup(groupPlan);
        return new ServerGroupDeploymentPlanBuilderImpl(this, setPlan);
    }

    @Override
    public ServerGroupDeploymentPlanBuilder allowPercentageFailures(int serverFailurePercentage) {
        DeploymentSetPlanImpl setPlan = getCurrentDeploymentSetPlan();
        ServerGroupDeploymentPlan groupPlan = setPlan.getLatestServerGroupDeploymentPlan();
        if (groupPlan == null) {
            throw ControllerClientLogger.ROOT_LOGGER.notConfigured(ServerGroupDeploymentPlan.class.getSimpleName());
        }
        groupPlan = groupPlan.createAllowFailurePercentage(serverFailurePercentage);
        setPlan = setPlan.storeServerGroup(groupPlan);
        return new ServerGroupDeploymentPlanBuilderImpl(this, setPlan);
    }

}
