/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain.impl;

import org.jboss.as.controller.client.helpers.domain.InitialDeploymentPlanBuilder;


/**
 * Factory for an {@link InitialDeploymentPlanBuilder}. Core purpose is to hide
 * the builder implementation classes from client yet provide a hook for external
 * {@link org.jboss.as.controller.client.helpers.domain.DomainDeploymentManager} implementations to create builders.
 *
 * @author Brian Stansberry
 */
public class InitialDeploymentPlanBuilderFactory {

    /** Prevent instantiation */
    private InitialDeploymentPlanBuilderFactory() {}

    public static InitialDeploymentPlanBuilder newInitialDeploymentPlanBuilder(DeploymentContentDistributor contentDistributor) {
        return new InitialDeploymentPlanBuilderImpl(contentDistributor);
    }
}
