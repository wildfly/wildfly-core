/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import java.util.EnumMap;
import java.util.List;

/**
 * The deployer chains service value object.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class DeployerChains {
    private final EnumMap<Phase, List<RegisteredDeploymentUnitProcessor>> phases;

    DeployerChains(final EnumMap<Phase, List<RegisteredDeploymentUnitProcessor>> phases) {
        this.phases = phases;
    }

    List<RegisteredDeploymentUnitProcessor> getChain(Phase phase) {
        return phases.get(phase);
    }
}
