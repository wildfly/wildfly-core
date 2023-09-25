/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceBuilder;

import java.util.List;

/**
 * @author Stuart Douglas
 */
public class DeploymentCompleteServiceProcessor implements DeploymentUnitProcessor {

    public static final ServiceName SERVICE_NAME = ServiceName.of("deploymentCompleteService");

    public static ServiceName serviceName(final ServiceName deploymentUnitServiceName) {
        return deploymentUnitServiceName.append(SERVICE_NAME);
    }

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        if(deploymentUnit.getParent() == null) {
            for(final DeploymentUnit subDeployment : deploymentUnit.getAttachmentList(Attachments.SUB_DEPLOYMENTS)) {
                deploymentUnit.addToAttachmentList(Attachments.DEPLOYMENT_COMPLETE_SERVICES, serviceName(subDeployment.getServiceName()));
            }
        }

        final ServiceBuilder<?> sb = phaseContext.getServiceTarget().addService(serviceName(deploymentUnit.getServiceName()), Service.NULL);
        final List<ServiceName> deploymentCompleteServices = deploymentUnit.getAttachmentList(Attachments.DEPLOYMENT_COMPLETE_SERVICES);
        if (deploymentCompleteServices != null) {
            for (final ServiceName deploymentCompleteService : deploymentCompleteServices) {
                sb.requires(deploymentCompleteService);
            }
        }
        sb.install();
    }

    @Override
    public void undeploy(final DeploymentUnit deploymentUnit) {
        deploymentUnit.removeAttachment(Attachments.DEPLOYMENT_COMPLETE_SERVICES);
    }
}
