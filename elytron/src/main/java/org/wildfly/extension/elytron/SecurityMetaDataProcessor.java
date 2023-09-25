/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.server.security.SecurityMetaData.ATTACHMENT_KEY;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.security.SecurityMetaData;

/**
 * A {@code DeploymentUnitProcessor} to associate a {@code SecurityMetaData} instance with each {@code DeploymentUnit}.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class SecurityMetaDataProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        /*
         * The SecurityMetaData is pro-actively attached to the deployment early to allow other processors
         * to update it to build a security policy, also references can be cached to receive subsequent updates.
         */
        phaseContext.getDeploymentUnit().putAttachment(ATTACHMENT_KEY, new SecurityMetaData());
    }

    @Override
    public void undeploy(DeploymentUnit deploymentUnit) {
        deploymentUnit.removeAttachment(ATTACHMENT_KEY);
    }

}
