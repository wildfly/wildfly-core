/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain.impl;

import org.jboss.as.controller.client.helpers.domain.DeployDeploymentPlanBuilder;
import org.jboss.as.controller.client.helpers.domain.ServerGroupDeploymentPlanBuilder;


/**
 * Variant of a {@link DeploymentPlanBuilderImpl} that exposes
 * directives that are only applicable following a <code>deploy</code>
 * or <code>replace</code> directive.
 *
 * @author Brian Stansberry
 */
class DeployDeploymentPlanBuilderImpl extends DeploymentPlanBuilderImpl implements DeployDeploymentPlanBuilder {

    DeployDeploymentPlanBuilderImpl(DeploymentPlanBuilderImpl existing, DeploymentSetPlanImpl setPlan) {
        super(existing, setPlan);
    }

    @Override
    public ServerGroupDeploymentPlanBuilder toServerGroup(String serverGroupName) {
        return super.toServerGroup(serverGroupName);
    }
}
