/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain.impl;

import org.jboss.as.controller.client.helpers.domain.InitialDeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.domain.InitialDeploymentSetBuilder;


/**
 * Variant of a {@link DeploymentPlanBuilderImpl} that is meant
 * to be used at the initial stages of the building process, when directives that
 * pertain to the entire plan can be applied.
 *
 * @author Brian Stansberry
 */
class InitialDeploymentPlanBuilderImpl extends InitialDeploymentSetBuilderImpl implements InitialDeploymentPlanBuilder  {

    /**
     * Constructs a new InitialDeploymentPlanBuilder
     */
    InitialDeploymentPlanBuilderImpl(DeploymentContentDistributor deploymentDistributor) {
        super(deploymentDistributor);
    }

    private InitialDeploymentPlanBuilderImpl(DeploymentPlanBuilderImpl existing, boolean globalRollback) {
        super(existing, globalRollback);
    }

    @Override
    public InitialDeploymentSetBuilder withRollbackAcrossGroups() {
        return new InitialDeploymentPlanBuilderImpl(this, true);
    }
}
