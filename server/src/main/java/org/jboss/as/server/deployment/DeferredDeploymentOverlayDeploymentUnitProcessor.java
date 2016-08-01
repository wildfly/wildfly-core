/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
