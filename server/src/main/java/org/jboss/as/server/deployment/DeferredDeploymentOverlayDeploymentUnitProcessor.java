/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.deployment.module.TempFileProviderService;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;

/**
 * Deployment unit processor that adds content overrides to the VFS filesystem for overlays that could not be initially resolved.
 *
 *
 *
 * @author Stuart Douglas
 */
public class DeferredDeploymentOverlayDeploymentUnitProcessor extends DeploymentOverlayDeploymentUnitProcessor {

    public DeferredDeploymentOverlayDeploymentUnitProcessor(final ContentRepository contentRepository) {
        super(contentRepository);
    }

    @Override
    protected void handleEntryWithFileParent(Map<String, byte[]> deferred, Map.Entry<String, byte[]> entry, String path, VirtualFile parent) {
        ServerLogger.DEPLOYMENT_LOGGER.couldNotMountOverlay(path, parent);
    }

    @Override
    protected void handleExplodedEntryWithDirParent(DeploymentUnit deploymentUnit, VirtualFile content, VirtualFile mountPoint,
            Map<String, MountedDeploymentOverlay> mounts, String overLayPath) throws IOException {
        Closeable handle = VFS.mountReal(content.getPhysicalFile(), mountPoint);
        MountedDeploymentOverlay mounted = new MountedDeploymentOverlay(handle, content.getPhysicalFile(), mountPoint, TempFileProviderService.provider());
        deploymentUnit.addToAttachmentList(MOUNTED_FILES, mounted);
        mounts.put(overLayPath, mounted);
    }

    @Override
    protected Map<String, byte[]> getDeferredAttachment(DeploymentUnit deploymentUnit) {
        return deploymentUnit.getAttachment(DEFERRED_OVERLAYS);
    }

    @Override
    protected Map<String, MountedDeploymentOverlay> getMountsAttachment(DeploymentUnit deploymentUnit) {
        return deploymentUnit.getAttachment(Attachments.DEPLOYMENT_OVERLAY_LOCATIONS);
    }

    @Override
    protected Map<String, byte[]> getOverlays(DeploymentUnit deploymentUnit) {
        return deploymentUnit.getAttachment(DEFERRED_OVERLAYS);
    }
}
