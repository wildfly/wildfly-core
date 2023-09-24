/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import java.io.IOException;
import java.util.List;
import java.util.jar.Manifest;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.DeploymentUtils;
import org.jboss.vfs.VFSUtils;
import org.jboss.vfs.VirtualFile;

/**
 * Deployment unit processor that attaches the deployment root manifest to the context.
 *
 * It does nothing if the manifest is already attached or there is no manifest in the deployment root file.
 *
 * @author Thomas.Diesler@jboss.com
 * @author Stuart Douglas
 * @since 14-Oct-2010
 */
public class ManifestAttachmentProcessor implements DeploymentUnitProcessor {

    /**
     * Process the deployment root for the manifest.
     *
     * @param phaseContext the deployment unit context
     * @throws DeploymentUnitProcessingException
     */
    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {

        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        List<ResourceRoot> resourceRoots = DeploymentUtils.allResourceRoots(deploymentUnit);
        for (ResourceRoot resourceRoot : resourceRoots) {
            if (IgnoreMetaInfMarker.isIgnoreMetaInf(resourceRoot)) {
                continue;
            }
            Manifest manifest = getManifest(resourceRoot);
            if (manifest != null)
                resourceRoot.putAttachment(Attachments.MANIFEST, manifest);
        }
    }

    public static Manifest getManifest(ResourceRoot resourceRoot) throws DeploymentUnitProcessingException {
        Manifest manifest = resourceRoot.getAttachment(Attachments.MANIFEST);
        if (manifest == null) {
            final VirtualFile deploymentRoot = resourceRoot.getRoot();
            try {
                manifest = VFSUtils.getManifest(deploymentRoot);
            } catch (IOException e) {
                throw ServerLogger.ROOT_LOGGER.failedToGetManifest(deploymentRoot, e);
            }
        }
        return manifest;
    }

    @Override
    public void undeploy(final DeploymentUnit context) {
        List<ResourceRoot> resourceRoots = DeploymentUtils.allResourceRoots(context);
        for (ResourceRoot resourceRoot : resourceRoots) {
            resourceRoot.removeAttachment(Attachments.MANIFEST);
        }
    }
}
