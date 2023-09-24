/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import static java.util.jar.Attributes.Name.EXTENSION_NAME;
import static java.util.jar.Attributes.Name.IMPLEMENTATION_VENDOR_ID;
import static java.util.jar.Attributes.Name.IMPLEMENTATION_VERSION;
import static java.util.jar.Attributes.Name.SPECIFICATION_VERSION;

import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.Services;

/**
 * A processor which reads the Extension-Name attribute from a manifest
 *
 * @author Stuart Douglas
 */
public final class ManifestExtensionNameProcessor implements DeploymentUnitProcessor {

    /** {@inheritDoc} */
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        // we only want to process top level jar deployments
        if (deploymentUnit.getParent() != null) {
            return;
        }

        final ResourceRoot deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);

        if (!deploymentRoot.getRoot().getName().endsWith(".jar")) {
            return;
        }
        // we are only interested in the root manifest
        // there should not be any additional resource roots for this type of deployment anyway
        final Manifest manifest = deploymentRoot.getAttachment(Attachments.MANIFEST);
        if (manifest == null) {
            return;
        }
        final Attributes mainAttributes = manifest.getMainAttributes();
        final String extensionName = mainAttributes.getValue(EXTENSION_NAME);
        ServerLogger.DEPLOYMENT_LOGGER.debugf("Found Extension-Name manifest entry %s in %s", extensionName, deploymentRoot.getRoot().getPathName());
        if (extensionName == null) {
            // no entry
            return;
        }
        final String implVersion = mainAttributes.getValue(IMPLEMENTATION_VERSION);
        final String implVendorId = mainAttributes.getValue(IMPLEMENTATION_VENDOR_ID);
        final String specVersion = mainAttributes.getValue(SPECIFICATION_VERSION);
        final ExtensionInfo info = new ExtensionInfo(extensionName, specVersion, implVersion, implVendorId);
        deploymentUnit.putAttachment(Attachments.EXTENSION_INFORMATION, info);

        phaseContext.addToAttachmentList(Attachments.NEXT_PHASE_DEPS, Services.JBOSS_DEPLOYMENT_EXTENSION_INDEX);
    }

}
