/*
 * Copyright 2020 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.server.security.SecurityMetaData.ATTACHMENT_KEY;
import static org.jboss.as.server.security.VirtualDomainMarkerUtility.isVirtualDomainRequired;
import static org.jboss.as.server.security.VirtualDomainMarkerUtility.virtualDomainName;
import static org.jboss.as.server.security.VirtualDomainUtil.setTopLevelDeploymentSecurityMetaData;

import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.security.SecurityMetaData;
import org.jboss.msc.service.ServiceName;

/**
 * A {@code DeploymentUnitProcessor} to set the {@code ServiceName} of any virtual security domain to be used
 * by the deployment.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class VirtualSecurityDomainNameProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        SecurityMetaData securityMetaData = deploymentUnit.getAttachment(ATTACHMENT_KEY);
        if (securityMetaData != null && isVirtualDomainRequired(deploymentUnit)) {
            ServiceName virtualDomainName = virtualDomainName(deploymentUnit);
            securityMetaData.setSecurityDomain(virtualDomainName);
            setTopLevelDeploymentSecurityMetaData(deploymentUnit, virtualDomainName);
        }
    }

}
