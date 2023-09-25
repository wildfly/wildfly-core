/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.jboss.as.server.security.VirtualDomainMarkerUtility.isVirtualDomainRequired;
import static org.jboss.as.server.security.VirtualDomainMarkerUtility.virtualDomainName;
import static org.jboss.as.server.security.VirtualDomainUtil.clearVirtualDomainMetaDataSecurityDomain;
import static org.jboss.as.server.security.VirtualDomainUtil.configureVirtualDomain;
import static org.jboss.as.server.security.VirtualDomainUtil.isVirtualDomainCreated;
import static org.jboss.as.server.security.VirtualDomainUtil.getVirtualDomainMetaData;

import java.util.function.Consumer;

import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.security.VirtualDomainMetaData;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.security.auth.server.SecurityDomain;

/**
 * A {@link DeploymentUnitProcessor} to install a virtual {@link SecurityDomain} if required.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class VirtualSecurityDomainProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        if (deploymentUnit.getParent() != null || !isVirtualDomainRequired(deploymentUnit)) {
            return;  // Only interested in installation if this is really the root deployment.
        }

        if (! isVirtualDomainCreated(deploymentUnit)) {
            ServiceName virtualDomainName = virtualDomainName(deploymentUnit);
            VirtualDomainMetaData virtualDomainMetaData = getVirtualDomainMetaData(deploymentUnit);

            ServiceTarget serviceTarget = phaseContext.getServiceTarget();
            ServiceBuilder<?> serviceBuilder = serviceTarget.addService(virtualDomainName);

            SecurityDomain.Builder virtualDomainBuilder = SecurityDomain.builder();
            configureVirtualDomain(virtualDomainMetaData, virtualDomainBuilder);
            final SecurityDomain virtualDomain = virtualDomainBuilder.build();
            if (virtualDomainMetaData != null) {
                virtualDomainMetaData.setSecurityDomain(virtualDomain);
            }

            final Consumer<SecurityDomain> consumer = serviceBuilder.provides(virtualDomainName);

            serviceBuilder.setInstance(Service.newInstance(consumer, virtualDomain));
            serviceBuilder.setInitialMode(Mode.ON_DEMAND);
            serviceBuilder.install();
        }
    }

    @Override
    public void undeploy(DeploymentUnit deploymentUnit) {
        clearVirtualDomainMetaDataSecurityDomain(deploymentUnit);
    }

}
