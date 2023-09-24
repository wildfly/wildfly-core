/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

import java.util.concurrent.Future;

/**
 * Primary deployment interface for a JBoss AS Domain Controller.
 *
 * @author Brian Stansberry
 */
public interface DomainDeploymentManager {

    /**
     * Initiates the creation of a new {@link DeploymentPlan}.
     *
     * @return builder object for the {@link DeploymentPlan}
     */
    InitialDeploymentPlanBuilder newDeploymentPlan();

    /**
     * Execute the deployment plan.
     *
     * @param plan the deployment plan
     *
     * @return the results of the deployment plan
     *
     * @return {@link Future} from which the results of the deployment plan can
     *         be obtained
     */
    Future<DeploymentPlanResult> execute(DeploymentPlan plan);
}
