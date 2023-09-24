/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client.helpers.standalone;


/**
 * Describes the results of executing a {@link DeploymentAction} on a single
 * server.
 *
 * @author Brian Stansberry
 */
public interface ServerDeploymentActionResult extends ServerUpdateActionResult {

    /**
     * Overrides the superclass to declare a more specific return type.
     * {@inheritDoc}
     */
    ServerDeploymentActionResult getRollbackResult();
}
