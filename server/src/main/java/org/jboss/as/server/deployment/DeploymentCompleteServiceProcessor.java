/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
