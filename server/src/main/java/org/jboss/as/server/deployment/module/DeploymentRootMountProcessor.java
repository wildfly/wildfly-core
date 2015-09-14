/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.deployment.module;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.jar.JarFile;

import org.jboss.as.server.Utils;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.ExplodedDeploymentMarker;
import org.jboss.as.server.deployment.MountType;
import org.jboss.modules.ResourceLoader;
import org.jboss.modules.ResourceLoaders;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;

/**
 * Deployment processor responsible for mounting and attaching the resource root for this deployment.
 *
 * @author John Bailey
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class DeploymentRootMountProcessor implements DeploymentUnitProcessor {

    static final String WAR_EXTENSION = ".war";
    static final String WAB_EXTENSION = ".wab";

    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        if(deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT) != null) {
            return;
        }
        final File deploymentContentsFile = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_CONTENTS);
        final VirtualFile deploymentContents = VFS.getChild(deploymentContentsFile.toURI());

        // internal deployments do not have any contents, so there is nothing to mount
        if (deploymentContents == null) return;

        final VirtualFile deploymentRoot;
        final MountHandle mountHandle;
        final String deploymentName = deploymentUnit.getName().toLowerCase(Locale.ENGLISH);
        if (deploymentContents.isDirectory()) {
            // use the contents directly
            deploymentRoot = deploymentContents;
            // nothing was mounted
            mountHandle = null;
            ExplodedDeploymentMarker.markAsExplodedDeployment(deploymentUnit);
        } else {
            // The mount point we will use for the repository file
            deploymentRoot = VFS.getChild("content/" + deploymentName);

            boolean failed = false;
            Closeable handle = null;
            try {
                final MountType type;
                if (explode(deploymentName)) {
                    type = MountType.EXPANDED;
                } else if (deploymentName.endsWith(".xml")) {
                    type = MountType.REAL;
                } else {
                    type = MountType.ZIP;
                }
                handle = mountDeploymentContent(deploymentContents, deploymentRoot, type);
                mountHandle = new MountHandle(handle);
            } catch (IOException e) {
                failed = true;
                throw ServerLogger.ROOT_LOGGER.deploymentMountFailed(e);
            } finally {
                if(failed) {
                    Utils.safeClose(handle);
                }
            }
        }
        ResourceLoader loader;
        if (deploymentContentsFile.isDirectory()) {
            loader = ResourceLoaders.createFileResourceLoader(deploymentContentsFile.getName(), deploymentContentsFile);
        } else {
            try {
                loader = ResourceLoaders.createJarResourceLoader(deploymentContentsFile.getName(), new JarFile(deploymentContentsFile));
            } catch (IOException e) {
                throw ServerLogger.ROOT_LOGGER.deploymentMountFailed(e);
            }
        }
        final ResourceRoot resourceRoot = new ResourceRoot(loader, deploymentRoot, mountHandle);
        ModuleRootMarker.mark(resourceRoot);
        deploymentUnit.putAttachment(Attachments.DEPLOYMENT_ROOT, resourceRoot);
        deploymentUnit.putAttachment(Attachments.MODULE_SPECIFICATION, new ModuleSpecification());
    }

    private Closeable mountDeploymentContent(final VirtualFile contents, VirtualFile mountPoint, MountType type) throws IOException {
        switch (type) {
            case ZIP:
                return VFS.mountZip(contents, mountPoint);
            case EXPANDED:
                return VFS.mountZipExpanded(contents, mountPoint);
            case REAL:
                return VFS.mountReal(contents.getPhysicalFile(), mountPoint);
            default:
                throw ServerLogger.ROOT_LOGGER.unknownMountType(type);
        }
    }

    private boolean explode(final String depName) {
        return depName.endsWith(WAR_EXTENSION) || depName.endsWith(WAB_EXTENSION);
    }

    public void undeploy(DeploymentUnit context) {
        final ResourceRoot resourceRoot = context.removeAttachment(Attachments.DEPLOYMENT_ROOT);
        if (resourceRoot != null) {
            final Closeable mountHandle = resourceRoot.getMountHandle();
            Utils.safeClose(mountHandle);
        }
    }
}
