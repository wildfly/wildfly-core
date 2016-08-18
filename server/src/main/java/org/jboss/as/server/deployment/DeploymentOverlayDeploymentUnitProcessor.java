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

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.deploymentoverlay.DeploymentOverlayIndex;
import org.jboss.as.server.deployment.module.ResourceRoot;

/**
 * Deployment unit processor that adds content overrides to the WildFly runtime.
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class DeploymentOverlayDeploymentUnitProcessor implements DeploymentUnitProcessor {

    private final ContentRepository contentRepository;

    public DeploymentOverlayDeploymentUnitProcessor(final ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ResourceRoot deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);

        Map<String, byte[]> overlayEntries = getOverlays(deploymentUnit);
        if (overlayEntries == null) {
            return;
        }
        final Set<String> paths = new HashSet<>();
        String path;
        for (final Map.Entry<String, byte[]> entry : overlayEntries.entrySet()) {
            path = entry.getKey();
            if (!paths.contains(path)) {
                paths.add(path);
                File content = contentRepository.getContent(entry.getValue());
                deploymentRoot.getLoader().addOverlay(path, content);
            }
        }
    }

    protected Map<String, byte[]> getOverlays(DeploymentUnit deploymentUnit) {
        DeploymentOverlayIndex overlays = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_OVERLAY_INDEX);
        if (overlays == null) {
            return null;
        }
        Map<String, byte[]> overlayEntries = overlays.getOverlays(deploymentUnit.getName());
        return overlayEntries;
    }

    @Override
    public void undeploy(final DeploymentUnit context) {
    }

}
