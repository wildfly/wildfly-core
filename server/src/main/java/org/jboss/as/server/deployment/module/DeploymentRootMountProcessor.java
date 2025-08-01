/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentMountProvider;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.ExplodedDeploymentMarker;
import org.jboss.as.server.deployment.MountExplodedMarker;
import org.jboss.as.server.deployment.MountType;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VFSUtils;
import org.jboss.vfs.VirtualFile;

/**
 * Deployment processor responsible for mounting and attaching the resource root for this deployment.
 *
 * @author John Bailey
 */
public class DeploymentRootMountProcessor implements DeploymentUnitProcessor {

    private final File serverContentDir;

    public DeploymentRootMountProcessor(File serverContentDir) {
        this.serverContentDir = serverContentDir;
    }

    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        if(deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT) != null) {
            return;
        }
        final DeploymentMountProvider deploymentMountProvider = deploymentUnit.getAttachment(Attachments.SERVER_DEPLOYMENT_REPOSITORY);
        if(deploymentMountProvider == null) {
            throw ServerLogger.ROOT_LOGGER.noDeploymentRepositoryAvailable();
        }

        final String deploymentName = deploymentUnit.getName();
        final VirtualFile deploymentContents = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_CONTENTS);

        // internal deployments do not have any contents, so there is nothing to mount
        if (deploymentContents == null)
            return;

        final VirtualFile deploymentRoot;
        final MountHandle mountHandle;
        if (deploymentContents.isDirectory()) {
            // use the contents directly
            deploymentRoot = deploymentContents;
            // nothing was mounted
            mountHandle = null;
            ExplodedDeploymentMarker.markAsExplodedDeployment(deploymentUnit);

        } else {
            // The mount point we will use for the repository file
            deploymentRoot = VFS.getChild(serverContentDir.getAbsolutePath() + "/" + deploymentName);

            boolean failed = false;
            Closeable handle = null;
            try {
                final boolean mountExploded = MountExplodedMarker.isMountExploded(deploymentUnit);
                final MountType type;
                if(mountExploded) {
                    type = MountType.EXPANDED;
                } else if (deploymentName.endsWith(".xml")) {
                    type = MountType.REAL;
                } else {
                    type = MountType.ZIP;
                }
                handle = deploymentMountProvider.mountDeploymentContent(deploymentContents, deploymentRoot, type);
                mountHandle = MountHandle.create(handle);
            } catch (IOException e) {
                failed = true;
                throw ServerLogger.ROOT_LOGGER.deploymentMountFailed(e);
            } finally {
                if(failed) {
                    VFSUtils.safeClose(handle);
                }
            }
        }
        final ResourceRoot resourceRoot = new ResourceRoot(deploymentRoot, mountHandle);
        ModuleRootMarker.mark(resourceRoot);
        deploymentUnit.putAttachment(Attachments.DEPLOYMENT_ROOT, resourceRoot);
        deploymentUnit.putAttachment(Attachments.MODULE_SPECIFICATION, new ModuleSpecification());
    }

    public void undeploy(DeploymentUnit context) {
        final ResourceRoot resourceRoot = context.removeAttachment(Attachments.DEPLOYMENT_ROOT);
        if (resourceRoot != null) {
            final Closeable mountHandle = resourceRoot.getMountHandle();
            VFSUtils.safeClose(mountHandle);
        }
    }
}
