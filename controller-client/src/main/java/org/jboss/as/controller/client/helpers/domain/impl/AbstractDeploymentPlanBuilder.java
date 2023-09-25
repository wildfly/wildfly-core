/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain.impl;

import org.jboss.as.controller.client.helpers.domain.DeploymentAction;
import org.jboss.as.controller.client.helpers.domain.DeploymentPlan;


/**
 * Builder capable of creating a {@link DeploymentPlanImpl}.
 *
 * @author Brian Stansberry
 */
class AbstractDeploymentPlanBuilder  {

    private final DeploymentSetPlanImpl setPlan;
    private final boolean rollbackAcrossGroups;

    AbstractDeploymentPlanBuilder() {
        this.setPlan = new DeploymentSetPlanImpl();
        this.rollbackAcrossGroups = false;
    }

    AbstractDeploymentPlanBuilder(AbstractDeploymentPlanBuilder existing, final boolean rollbackAcrossGroups) {
        this.setPlan = existing.setPlan;
        this.rollbackAcrossGroups = rollbackAcrossGroups;
    }

    AbstractDeploymentPlanBuilder(AbstractDeploymentPlanBuilder existing, DeploymentSetPlanImpl setPlan) {
        this.setPlan = setPlan;
        this.rollbackAcrossGroups = existing.rollbackAcrossGroups;
    }

    public DeploymentAction getLastAction() {
        return getCurrentDeploymentSetPlan().getLastAction();
    }

    DeploymentSetPlanImpl getCurrentDeploymentSetPlan() {
        return setPlan;
    }

    /**
     * Creates the deployment plan.
     *
     * @return the deployment plan
     */
    public DeploymentPlan build() {
        return new DeploymentPlanImpl(setPlan, rollbackAcrossGroups);
    }
}
