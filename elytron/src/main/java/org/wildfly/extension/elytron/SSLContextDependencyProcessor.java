/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import javax.net.ssl.SSLContext;

import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;

/**
 * A simple {@link DeploymentUnitProcessor} to ensure deployments wait until the default {@link SSLContext} has been registered.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class SSLContextDependencyProcessor implements DeploymentUnitProcessor {

    /**
     * @see org.jboss.as.server.deployment.DeploymentUnitProcessor#deploy(org.jboss.as.server.deployment.DeploymentPhaseContext)
     */
    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        phaseContext.addDeploymentDependency(DefaultSSLContextService.SERVICE_NAME, ElytronExtension.SSL_CONTEXT_KEY);
    }

}
