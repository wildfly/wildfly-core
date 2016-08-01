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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.deployment.module.TempFileProviderService;
import org.jboss.as.server.deploymentoverlay.DeploymentOverlayIndex;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;

/**
 * Deployment unit processor that adds content overrides to the VFS filesystem.
 *
 * This is a two phase process. First any overlays that can be easily resolved are mounted, however we may not be able
 * to mount all overlays because they may depend on VFS mounts that are set up by later structure processors (e.g. if
 * there is an overlay for ear/lib/mylib.jar/com/acme/MyClass.class it can't be mounted until the ear structure processor
 * has created the mount). These resource roots are identified and deferred to be processed at the end of the structure
 * phase.
 *
 * Note that we can't just process everything at the end, as we may need to replace the archives that are mounted by
 * these later processors
 *
 * @author Stuart Douglas
 */
public class DeploymentOverlayDeploymentUnitProcessor implements DeploymentUnitProcessor {

    private final ContentRepository contentRepository;

    protected static final AttachmentKey<AttachmentList<Closeable>> MOUNTED_FILES = AttachmentKey.createList(Closeable.class);
    protected static final AttachmentKey<Map<String, byte[]>> DEFERRED_OVERLAYS = AttachmentKey.create(Map.class);

    public DeploymentOverlayDeploymentUnitProcessor(final ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ResourceRoot deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);

        Map<String, MountedDeploymentOverlay> mounts = getMountsAttachment(deploymentUnit);

        Map<String, byte[]> deferred = getDeferredAttachment(deploymentUnit);

        Map<String, byte[]> overlayEntries = getOverlays(deploymentUnit);
        if (overlayEntries == null) {
            return;
        }
        //exploded is true if this is a zip deployment that has been mounted exploded
        final boolean exploded = MountExplodedMarker.isMountExploded(deploymentUnit) && !ExplodedDeploymentMarker.isExplodedDeployment(deploymentUnit);
        final Set<String> paths = new HashSet<String>();

        for (final Map.Entry<String, byte[]> entry : overlayEntries.entrySet()) {
            String path = entry.getKey();
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            try {
                if (!paths.contains(path)) {
                    VirtualFile mountPoint = deploymentRoot.getRoot().getChild(path);

                    paths.add(path);
                    VirtualFile content = contentRepository.getContent(entry.getValue());
                    if (exploded) {
                        VirtualFile parent = mountPoint.getParent();
                        while (!parent.exists()) {
                            parent = parent.getParent();
                        }
                        //we need to check if the parent is a directory
                        //if it is a file we assume it is an archive that is yet to be mounted and we add it to the deferred list
                        if(parent.isDirectory()) {
                            handleExplodedEntryWithDirParent(deploymentUnit, content, mountPoint, mounts, path);
                        } else {
                            handleEntryWithFileParent(deferred, entry, path, parent);
                        }
                    } else {
                        VirtualFile parent = mountPoint.getParent();
                        List<VirtualFile> createParents = new ArrayList<>();
                        while (!parent.exists()) {
                            createParents.add(parent);
                            parent = parent.getParent();
                        }
                        //we need to check if the parent is a directory
                        //if it is a file we assume it is an archive that is yet to be mounted and we add it to the deferred list
                        if(parent.isDirectory()) {
                            if (isExplodedSubUnitOverlay(deploymentUnit, mountPoint, path)) {// like: war/*.html
                                copyFile(content.getPhysicalFile(), mountPoint.getPhysicalFile());
                                continue;
                            }
                            Collections.reverse(createParents);
                            for (VirtualFile file : createParents) {
                                Closeable closable = VFS.mountTemp(file, TempFileProviderService.provider());
                                deploymentUnit.addToAttachmentList(MOUNTED_FILES, closable);
                            }
                            Closeable handle = VFS.mountReal(content.getPhysicalFile(), mountPoint);
                            MountedDeploymentOverlay mounted = new MountedDeploymentOverlay(handle, content.getPhysicalFile(), mountPoint, TempFileProviderService.provider());
                            deploymentUnit.addToAttachmentList(MOUNTED_FILES, mounted);
                            mounts.put(path, mounted);
                        } else {
                            //we have an overlay that is targeted at a file, most likely a zip file that is yet to be mounted by a structure processor
                            //we take note of these overlays and try and mount them at the end of the STRUCTURE phase
                            handleEntryWithFileParent(deferred, entry, path, parent);
                        }

                    }
                }
            } catch (IOException e) {
                throw ServerLogger.ROOT_LOGGER.deploymentOverlayFailed(e, entry.getKey(), path);
            }
        }
    }

    private boolean isExplodedSubUnitOverlay(DeploymentUnit deploymentUnit, VirtualFile mountPoint, String path) {
        final List<ResourceRoot> childRes = deploymentUnit.getAttachmentList(Attachments.RESOURCE_ROOTS);
        if (childRes != null) {
            for (ResourceRoot rs: childRes) {
                if (path.startsWith(rs.getRoot().getName())) {
                    String relativePath = mountPoint.getPathNameRelativeTo(rs.getRoot());
                    if (relativePath != null
                            && relativePath.length() > 0
                            && SubExplodedDeploymentMarker.isSubExplodedResourceRoot(rs)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    protected void handleEntryWithFileParent(Map<String, byte[]> deferred, Map.Entry<String, byte[]> entry, String path, VirtualFile parent) {
        deferred.put(path, entry.getValue());
    }

    protected void handleExplodedEntryWithDirParent(DeploymentUnit deploymentUnit,
            VirtualFile content, VirtualFile mountPoint, Map<String, MountedDeploymentOverlay> mounts,
            String overLayPath) throws IOException{
        copyFile(content.getPhysicalFile(), mountPoint.getPhysicalFile());
    }

    protected Map<String, byte[]> getDeferredAttachment(DeploymentUnit deploymentUnit) {
        Map<String, byte[]> deferred = new HashMap<>();
        deploymentUnit.putAttachment(DEFERRED_OVERLAYS, deferred);
        return deferred;
    }

    protected Map<String, MountedDeploymentOverlay> getMountsAttachment(DeploymentUnit deploymentUnit) {
        Map<String, MountedDeploymentOverlay> mounts = new HashMap<String, MountedDeploymentOverlay>();
        deploymentUnit.putAttachment(Attachments.DEPLOYMENT_OVERLAY_LOCATIONS, mounts);
        return mounts;
    }

    protected Map<String, byte[]> getOverlays(DeploymentUnit deploymentUnit) {
        DeploymentOverlayIndex overlays = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_OVERLAY_INDEX);
        if(overlays == null) {
            return null;
        }
        Map<String, byte[]> overlayEntries = overlays.getOverlays(deploymentUnit.getName());
        return overlayEntries;
    }

    @Override
    public void undeploy(final DeploymentUnit context) {
        for (Closeable closable : context.getAttachmentList(MOUNTED_FILES)) {
            try {
                closable.close();
            } catch (IOException e) {
                ServerLogger.DEPLOYMENT_LOGGER.failedToUnmountContentOverride(e);
            }
        }

    }

    protected static void copyFile(final File src, final File dest) throws IOException {
        final InputStream in = new BufferedInputStream(new FileInputStream(src));
        try {
            copyFile(in, dest);
        } finally {
            close(in);
        }
    }

    protected static void copyFile(final InputStream in, final File dest) throws IOException {
        dest.getParentFile().mkdirs();
        byte[] buff = new byte[1024];
        final OutputStream out = new BufferedOutputStream(new FileOutputStream(dest));
        try {
            int i = in.read(buff);
            while (i > 0) {
                out.write(buff, 0, i);
                i = in.read(buff);
            }
        } finally {
            close(out);
        }
    }


    protected static void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignore) {
        }
    }
}
