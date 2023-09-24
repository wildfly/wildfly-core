/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.domain;

/**
 * Exception indicating a given {@link DeploymentPlan} is invalid since it
 * could leave the domain in an invalid state.
 *
 * @author Brian Stansberry
 */
public class InvalidDeploymentPlanException extends Exception {

    private static final long serialVersionUID = 6442943555765667251L;

    /**
     * Constructs a new InvalidDeploymentPlanException with the given message.
     *
     * @param message the message
     */
    public InvalidDeploymentPlanException(String message) {
        super(message);
    }

    /**
     * Constructs a new InvalidDeploymentPlanException with the given message and cause.
     *
     * @param message the message
     * @param cause the cause
     */
    public InvalidDeploymentPlanException(String message, Exception cause) {
        super(message, cause);
    }

}
