/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.Services;
import org.jboss.as.server.moduleservice.ExtensionIndex;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.msc.service.ServiceController;

/**
 * A processor which stores extension information for deployed libraries into the {@link org.jboss.as.server.moduleservice.ExtensionIndexService}.
 *
 * @author Stuart Douglas
 */
public final class ModuleExtensionNameProcessor implements DeploymentUnitProcessor {

    /** {@inheritDoc} */
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        final ExtensionInfo extensionInfo = deploymentUnit.getAttachment(Attachments.EXTENSION_INFORMATION);
        if (extensionInfo == null) {
            return;
        }
        final ServiceController<?> extensionIndexController = phaseContext.getServiceRegistry().getRequiredService(
                Services.JBOSS_DEPLOYMENT_EXTENSION_INDEX);
        final ExtensionIndex extensionIndexService = (ExtensionIndex) extensionIndexController.getValue();
        final ModuleIdentifier moduleIdentifier = deploymentUnit.getAttachment(Attachments.MODULE_IDENTIFIER);
        extensionIndexService.addDeployedExtension(moduleIdentifier, extensionInfo);
    }

    /** {@inheritDoc} */
    public void undeploy(final DeploymentUnit deploymentUnit) {
        final ExtensionInfo extensionInfo = deploymentUnit.getAttachment(Attachments.EXTENSION_INFORMATION);
        if (extensionInfo == null) {
            return;
        }
        // we need to remove the extension on undeploy
        final ServiceController<?> extensionIndexController = deploymentUnit.getServiceRegistry().getRequiredService(
                Services.JBOSS_DEPLOYMENT_EXTENSION_INDEX);
        final ExtensionIndex extensionIndexService = (ExtensionIndex) extensionIndexController.getValue();
        final ModuleIdentifier moduleIdentifier = deploymentUnit.getAttachment(Attachments.MODULE_IDENTIFIER);

        extensionIndexService.removeDeployedExtension(extensionInfo.getName(), moduleIdentifier);

    }
}
