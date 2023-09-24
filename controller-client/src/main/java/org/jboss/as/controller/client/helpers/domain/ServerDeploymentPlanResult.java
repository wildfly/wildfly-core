/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

import java.util.Map;
import java.util.UUID;



/**
 * Encapsulates the results of executing a {@link DeploymentSetPlan} on
 * a particular server.
 *
 * @author Brian Stansberry
 */
public interface ServerDeploymentPlanResult {

    /**
     * Gets the name of the server.
     *
     * @return the name. Will not be <code>null</code>
     */
    String getServerName();

    /**
     * Gets the result of a {@link DeploymentAction} action associated with
     * the deployment set plan.
     *
     * @return the result
     */
    Map<UUID, ServerUpdateResult> getDeploymentActionResults();
}
