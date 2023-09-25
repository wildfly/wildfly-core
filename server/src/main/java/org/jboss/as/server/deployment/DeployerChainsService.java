/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import java.util.EnumMap;
import java.util.List;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

/**
 * Service wrapper for {@link org.jboss.as.server.deployment.DeployerChains}.
 *
 * @author John Bailey
 */
public class DeployerChainsService implements Service<DeployerChains> {
    private final DeployerChains deployerChains;

    public static void addService(final ServiceTarget serviceTarget, final EnumMap<Phase, List<RegisteredDeploymentUnitProcessor>> phases) {
        final DeployerChains deployerChains = new DeployerChains(phases);
        serviceTarget.addService(Services.JBOSS_DEPLOYMENT_CHAINS, new DeployerChainsService(deployerChains))
            .install();
    }

    public DeployerChainsService(DeployerChains deployerChains) {
        this.deployerChains = deployerChains;
    }

    public void start(StartContext context) throws StartException {
    }

    public void stop(StopContext context) {
    }

    public DeployerChains getValue() throws IllegalStateException, IllegalArgumentException {
        return deployerChains;
    }
}
