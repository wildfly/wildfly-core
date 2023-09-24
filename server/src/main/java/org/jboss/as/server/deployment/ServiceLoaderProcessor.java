/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.deployment.module.ModuleRootMarker;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.vfs.VFSUtils;
import org.jboss.vfs.VirtualFile;

/**
 * A processor which creates a service loader index.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ServiceLoaderProcessor implements DeploymentUnitProcessor {

    /**
     * {@inheritDoc}
     */
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final Map<String, List<String>> foundServices = new HashMap<String, List<String>>();
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ResourceRoot deploymentRoot = deploymentUnit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        if (deploymentRoot != null) {
            processRoot(deploymentRoot, foundServices);
        }
        final List<ResourceRoot> resourceRoots = deploymentUnit.getAttachmentList(Attachments.RESOURCE_ROOTS);
        for (ResourceRoot resourceRoot : resourceRoots) {
            if (!SubDeploymentMarker.isSubDeployment(resourceRoot) && ModuleRootMarker.isModuleRoot(resourceRoot))
                processRoot(resourceRoot, foundServices);
        }
        deploymentUnit.putAttachment(Attachments.SERVICES, new ServicesAttachment(foundServices));
    }

    private void processRoot(final ResourceRoot resourceRoot, final Map<String, List<String>> foundServices) throws DeploymentUnitProcessingException {
        final VirtualFile virtualFile = resourceRoot.getRoot();
        final VirtualFile child = virtualFile.getChild("META-INF/services");
        for (VirtualFile serviceType : child.getChildren()) {
            final String name = serviceType.getName();
            try {
                List<String> list = foundServices.get(name);
                if (list == null) {
                    foundServices.put(name, list = new ArrayList<String>());
                }
                final InputStream stream = serviceType.openStream();
                try {
                    final BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final int commentIdx = line.indexOf('#');
                        final String className;
                        if (commentIdx == -1) {
                            className = line.trim();
                        } else {
                            className = line.substring(0, commentIdx).trim();
                        }
                        if (className.length() == 0) {
                            continue;
                        }
                        list.add(className);
                    }
                } finally {
                    VFSUtils.safeClose(stream);
                }
            } catch (IOException e) {
                throw ServerLogger.ROOT_LOGGER.failedToReadVirtualFile(child, e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void undeploy(final DeploymentUnit context) {
        context.removeAttachment(Attachments.SERVICES);
    }
}
