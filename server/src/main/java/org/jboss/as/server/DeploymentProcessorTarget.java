/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server;

import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.Phase;

/**
 * A target for deployment processors to be added to.
 */
public interface DeploymentProcessorTarget {

    /**
     * Add a deployment processor.
     *
     * @param subsystemName The name of the subsystem registering this processor
     * @param phase the processor phase install into (must not be {@code null})
     * @param priority the priority within the selected phase
     * @param processor the processor to install
     */
    void addDeploymentProcessor(String subsystemName, Phase phase, int priority, DeploymentUnitProcessor processor);
}
