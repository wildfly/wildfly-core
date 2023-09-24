/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import org.jboss.msc.service.ServiceBuilder;

/**
 * Encapsulates a dependency of a {@link DeploymentUnitPhaseService}.
 * @author Paul Ferraro
 */
public interface DeploymentUnitPhaseDependency {

    /**
     * Registers this dependency with a DeploymentUnitPhaseService builder.
     * @param builder a DeploymentUnitPhaseService builder
     */
    void register(ServiceBuilder<?> builder);
}
