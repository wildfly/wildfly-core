/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service;

import org.jboss.as.server.deployment.DeploymentPhaseContext;

/**
 * Installs a service into the target associated with a deployment phase.
 * @author Paul Ferraro
 */
public interface DeploymentServiceInstaller {
    /**
     * Installs a service into the target associated with the deployment phase of the specified context.
     * @param context a deployment phase context
     */
    void install(DeploymentPhaseContext context);
}
