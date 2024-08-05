/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;


/**
 * Represents the status of a deployment on a server.
 */
public enum DeploymentStatus {
    /** @deprecated not used. {@link #STOPPED} is used for deployment resources that have never been deployed. */
    @Deprecated(forRemoval = true)
    NEW,
    OK,
    FAILED,
    STOPPED
}
