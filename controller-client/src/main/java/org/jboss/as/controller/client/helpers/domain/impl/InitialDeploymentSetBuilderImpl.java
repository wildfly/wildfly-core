/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain.impl;

import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.client.logging.ControllerClientLogger;
import org.jboss.as.controller.client.helpers.domain.InitialDeploymentSetBuilder;


/**
 * Variant of a {@link DeploymentPlanBuilderImpl} that is meant
 * to be used at the initial stages of the building process, when directives that
 * pertain to the entire {@link org.jboss.as.controller.client.helpers.domain.DeploymentSetPlan} can be applied.
 *
 * @author Brian Stansberry
 */
public class InitialDeploymentSetBuilderImpl extends DeploymentPlanBuilderImpl implements InitialDeploymentSetBuilder  {

    /**
     * Constructs a new InitialDeploymentPlanBuilder
     */
    InitialDeploymentSetBuilderImpl(DeploymentContentDistributor deploymentDistributor) {
        super(deploymentDistributor);
    }

    InitialDeploymentSetBuilderImpl(DeploymentPlanBuilderImpl existing, boolean globalRollback) {
        super(existing, globalRollback);
    }

    InitialDeploymentSetBuilderImpl(DeploymentPlanBuilderImpl existing, DeploymentSetPlanImpl setPlan) {
        super(existing, setPlan);
    }

    @Override
    public InitialDeploymentSetBuilder withGracefulShutdown(long timeout, TimeUnit timeUnit) {

        DeploymentSetPlanImpl currentSet = getCurrentDeploymentSetPlan();
        long period = timeUnit.toMillis(timeout);
        if (currentSet.isShutdown() && period != currentSet.getGracefulShutdownTimeout()) {
            throw ControllerClientLogger.ROOT_LOGGER.gracefulShutdownAlreadyConfigured(currentSet.getGracefulShutdownTimeout());
        }
        DeploymentSetPlanImpl newSet = currentSet.setGracefulTimeout(period);
        return new InitialDeploymentSetBuilderImpl(this, newSet);
    }

    @Override
    public InitialDeploymentSetBuilder withShutdown() {

        DeploymentSetPlanImpl currentSet = getCurrentDeploymentSetPlan();
        DeploymentSetPlanImpl newSet = currentSet.setShutdown();
        return new InitialDeploymentSetBuilderImpl(this, newSet);
    }

    @Override
    public InitialDeploymentSetBuilder withoutSingleServerRollback() {
        DeploymentSetPlanImpl currentSet = getCurrentDeploymentSetPlan();
        DeploymentSetPlanImpl newSet = currentSet.setNoRollback();
        return new InitialDeploymentSetBuilderImpl(this, newSet);
    }
}
