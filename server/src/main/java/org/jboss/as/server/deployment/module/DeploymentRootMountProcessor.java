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

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.jboss.as.server.Utils;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.ExplodedDeploymentMarker;
import org.wildfly.loaders.ResourceLoader;
import org.wildfly.loaders.ResourceLoaders;

/**
 * Deployment processor responsible for creating the resource root for this deployment.
 *
 * @author John Bailey
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class DeploymentRootMountProcessor implements DeploymentUnitProcessor {

    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        if (deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT) != null) {
            return;
        }
        final File deployment = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_CONTENTS);
        if (deployment.isDirectory()) {
            // nothing was mounted
            ExplodedDeploymentMarker.markAsExplodedDeployment(deploymentUnit);
        }
        ResourceLoader loader;
        try {
            final String deploymentName = deploymentUnit.getName();
            loader = ResourceLoaders.newResourceLoader(deploymentName, deployment, true);
        } catch (IOException e) {
            throw ServerLogger.ROOT_LOGGER.deploymentMountFailed(e);
        }
        final ResourceRoot resourceRoot = new ResourceRoot(loader);
        ModuleRootMarker.mark(resourceRoot);
        deploymentUnit.putAttachment(Attachments.DEPLOYMENT_ROOT, resourceRoot);
        deploymentUnit.putAttachment(Attachments.MODULE_SPECIFICATION, new ModuleSpecification());
    }

    public void undeploy(final DeploymentUnit du) {
        // clean up deployment root
        final ResourceRoot resourceRoot = du.removeAttachment(Attachments.DEPLOYMENT_ROOT);
        if (resourceRoot != null) {
            Utils.safeClose(resourceRoot.getLoader());
        }
        // clean up all additional resource roots
        final List<ResourceRoot> childRoots = du.getAttachmentList(Attachments.RESOURCE_ROOTS);
        if (childRoots != null) {
            for (final ResourceRoot childRoot : childRoots) {
                Utils.safeClose(childRoot.getLoader());
            }
        }
    }
}
