/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.service;

import java.util.Collection;
import java.util.List;

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

    /**
     * Returns a composite {@link DeploymentServiceInstaller} that installs the specified installers.
     * @param installers a variable number of installers
     * @return a composite installer
     */
    static DeploymentServiceInstaller combine(DeploymentServiceInstaller... installers) {
        return combine(List.of(installers));
    }

    /**
     * Returns a composite {@link DeploymentServiceInstaller} that installs the specified installers.
     * @param installers a collection of installers
     * @return a composite installer
     */
    static DeploymentServiceInstaller combine(Collection<? extends DeploymentServiceInstaller> installers) {
        return new DeploymentServiceInstaller() {
            @Override
            public void install(DeploymentPhaseContext context) {
                for (DeploymentServiceInstaller installer : installers) {
                    installer.install(context);
                }
            }
        };
    }
}
