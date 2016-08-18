/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

import static org.wildfly.loaders.deployment.Utils.getResourceName;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.as.server.Utils;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.deployment.module.ModuleRootMarker;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.modules.Resource;
import org.wildfly.loaders.deployment.ResourceLoader;

/**
 * A processor which creates a service loader index.
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class ServiceLoaderProcessor implements DeploymentUnitProcessor {

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
        final ResourceLoader loader = resourceRoot.getLoader();
        final Iterator<Resource> services = loader.iterateResources("META-INF/services", false);
        Resource service;
        while (services.hasNext()) {
            service = services.next();
            final String name = getResourceName(service.getName());
            try {
                List<String> list = foundServices.get(name);
                if (list == null) {
                    foundServices.put(name, list = new ArrayList<>());
                }
                final InputStream stream = service.openStream();
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
                    Utils.safeClose(stream);
                }
            } catch (IOException e) {
                throw ServerLogger.ROOT_LOGGER.failedToReadVirtualFile(service.getName(), e);
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
