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
 * Variant of a {@link DeploymentPlanBuilderImpl} that exposes
 * directives that are only applicable when controlling how a {@link org.jboss.as.controller.client.helpers.domain.DeploymentSetPlan}
 * should be applied to one or more server groups.
 *
 * @author Brian Stansberry
 */
class ServerGroupDeploymentPlanBuilderImpl extends InitialDeploymentSetBuilderImpl implements ServerGroupDeploymentPlanBuilder {

    ServerGroupDeploymentPlanBuilderImpl(DeploymentPlanBuilderImpl existing, DeploymentSetPlanImpl setPlan) {
        super(existing, setPlan);
    }

    @Override
    public RollbackDeploymentPlanBuilder withRollback() {
        DeploymentSetPlanImpl setPlan = getCurrentDeploymentSetPlan();
        ServerGroupDeploymentPlan groupPlan = setPlan.getLatestServerGroupDeploymentPlan();
        if (groupPlan == null) {
            throw ControllerClientLogger.ROOT_LOGGER.notConfigured(ServerGroupDeploymentPlan.class.getSimpleName());
        }
        groupPlan = groupPlan.createRollback();
        setPlan = setPlan.storeServerGroup(groupPlan);
        return new RollbackDeploymentPlanBuilderImpl(this, setPlan);
    }

    @Override
    public ServerGroupDeploymentPlanBuilder rollingToServers() {
        DeploymentSetPlanImpl setPlan = getCurrentDeploymentSetPlan();
        ServerGroupDeploymentPlan groupPlan = setPlan.getLatestServerGroupDeploymentPlan();
        if (groupPlan == null) {
            throw ControllerClientLogger.ROOT_LOGGER.notConfigured(ServerGroupDeploymentPlan.class.getSimpleName());
        }
        groupPlan = groupPlan.createRollingToServers();
        setPlan = setPlan.storeServerGroup(groupPlan);
        return new ServerGroupDeploymentPlanBuilderImpl(this, setPlan);
    }

    @Override
    public ServerGroupDeploymentPlanBuilder rollingToServerGroup(String serverGroupName) {
        DeploymentSetPlanImpl setPlan = getCurrentDeploymentSetPlan();
        ServerGroupDeploymentPlan groupPlan = new ServerGroupDeploymentPlan(serverGroupName);
        setPlan = setPlan.storeRollToServerGroup(groupPlan);
        return new ServerGroupDeploymentPlanBuilderImpl(this, setPlan);
    }

    @Override
    public ServerGroupDeploymentPlanBuilder toServerGroup(String serverGroupName) {
        DeploymentSetPlanImpl setPlan = getCurrentDeploymentSetPlan();
        ServerGroupDeploymentPlan groupPlan = new ServerGroupDeploymentPlan(serverGroupName);
        setPlan = setPlan.storeServerGroup(groupPlan);
        return new ServerGroupDeploymentPlanBuilderImpl(this, setPlan);
    }
}
