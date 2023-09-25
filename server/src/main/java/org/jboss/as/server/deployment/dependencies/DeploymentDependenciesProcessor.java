/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.dependencies;

import javax.xml.namespace.QName;

import org.jboss.as.server.DeployerChainAddHandler;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.ServerService;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentCompleteServiceProcessor;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.server.deployment.Services;
import org.jboss.as.server.deployment.jbossallxml.JBossAllXmlParserRegisteringProcessor;
import org.jboss.msc.service.ServiceName;

/**
 * Processor that handles inter-deployment dependencies. If this deployment has a dependency specified on
 * another deployment then the next phase will be set to passive, and a dependency on the other deployment will
 * be added.
 *
 * @author Stuart Douglas
 */
public class DeploymentDependenciesProcessor implements DeploymentUnitProcessor {

    private static final QName ROOT_1_0 = new QName(DeploymentDependenciesParserV_1_0.NAMESPACE_1_0, "jboss-deployment-dependencies");

    public static void registerJBossXMLParsers() {
        DeployerChainAddHandler.addDeploymentProcessor(ServerService.SERVER_NAME, Phase.STRUCTURE, Phase.STRUCTURE_REGISTER_JBOSS_ALL_DEPLOYMENT_DEPS, new JBossAllXmlParserRegisteringProcessor<DeploymentDependencies>(ROOT_1_0, DeploymentDependencies.ATTACHMENT_KEY, DeploymentDependenciesParserV_1_0.INSTANCE));
    }

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        if (deploymentUnit.hasAttachment(DeploymentDependencies.ATTACHMENT_KEY)) {
            if (deploymentUnit.getParent() != null) {
                ServerLogger.DEPLOYMENT_LOGGER.deploymentDependenciesAreATopLevelElement(deploymentUnit.getName());
            } else {
                processDependencies(phaseContext, deploymentUnit);
            }
        }

        if (deploymentUnit.getParent() != null) {
            DeploymentUnit parent = deploymentUnit.getParent();
            if (parent.hasAttachment(DeploymentDependencies.ATTACHMENT_KEY)) {
                processDependencies(phaseContext, parent);
            }
        }
    }

    private void processDependencies(final DeploymentPhaseContext phaseContext, final DeploymentUnit deploymentUnit) {
        final DeploymentDependencies deps = deploymentUnit.getAttachment(DeploymentDependencies.ATTACHMENT_KEY);
        if (!deps.getDependencies().isEmpty()) {
            for (final String deployment : deps.getDependencies()) {
                final ServiceName name =  DeploymentCompleteServiceProcessor.serviceName(Services.deploymentUnitName(deployment));
                phaseContext.addToAttachmentList(Attachments.NEXT_PHASE_DEPS, name);
            }
        }
    }

}
