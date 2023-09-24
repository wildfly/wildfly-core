/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

import org.jboss.as.controller.client.logging.ControllerClientLogger;

/**
 * {@link InvalidDeploymentPlanException} thrown when a deployment plan
 * specifies that a new version of content replace existing content of the same
 * unique name, but does not apply the replacement to all server groups that
 * have the existing content deployed.
 *
 * @author Brian Stansberry
 */
public class IncompleteDeploymentReplaceException extends InvalidDeploymentPlanException {

    private static final long serialVersionUID = -8322852398826927588L;

    public IncompleteDeploymentReplaceException(String deploymentName, String... missingGroups) {
        super(ControllerClientLogger.ROOT_LOGGER.incompleteDeploymentReplace(deploymentName, createMissingGroups(missingGroups)));
    }

    private static String createMissingGroups(String[] missingGroups) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < missingGroups.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(missingGroups[i]);
        }
        return sb.toString();
    }
}
