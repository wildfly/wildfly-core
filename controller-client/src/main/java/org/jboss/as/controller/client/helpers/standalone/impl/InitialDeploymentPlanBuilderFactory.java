/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.standalone.impl;

import org.jboss.as.controller.client.helpers.standalone.InitialDeploymentPlanBuilder;

/**
 * Factory for an {@link InitialDeploymentPlanBuilder}. Core purpose is to hide
 * the builder implementation classes from client yet provide a hook for external
 * {@link org.jboss.as.controller.client.helpers.standalone.ServerDeploymentManager} implementations to create builders.
 *
 * @author Brian Stansberry
 */
public class InitialDeploymentPlanBuilderFactory {

    /** Prevent instantiation */
    private InitialDeploymentPlanBuilderFactory() {}

    public static InitialDeploymentPlanBuilder newInitialDeploymentPlanBuilder() {
        return new DeploymentPlanBuilderImpl();
    }
}
