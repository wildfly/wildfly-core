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

package org.jboss.as.server.deployment.integration;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.ResourceLoaderSpec;
import org.wildfly.loaders.ResourceLoader;
import org.wildfly.loaders.ResourceLoaders;

/**
 * Recognize Seam deployments and add org.jboss.seam.int module to it.
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class Seam2Processor implements DeploymentUnitProcessor {

    private static final String SEAM_PROPERTIES = "seam.properties";
    private static final String SEAM_PROPERTIES_META_INF = "META-INF/" + SEAM_PROPERTIES;
    private static final String SEAM_PROPERTIES_WEB_INF = "WEB-INF/classes/" + SEAM_PROPERTIES;
    private static final String SEAM_COMPONENTS = "components.xml";
    private static final String SEAM_COMPONENTS_META_INF = "META-INF/" + SEAM_COMPONENTS;
    private static final String SEAM_COMPONENTS_WEB_INF = "WEB-INF/" + SEAM_COMPONENTS;
    private static final String SEAM_INT_JAR = "jboss-seam-int.jar";
    private static final ModuleIdentifier EXT_CONTENT_MODULE = ModuleIdentifier.create("org.jboss.integration.ext-content");
    private static final String[] SEAM_FILES = new String[] {
            SEAM_PROPERTIES,
            SEAM_PROPERTIES_META_INF,
            SEAM_PROPERTIES_WEB_INF,
            SEAM_COMPONENTS_META_INF,
            SEAM_COMPONENTS_WEB_INF
    };
    private ResourceRoot seamIntResourceRoot;

    /**
     * Lookup Seam integration resource loader.
     * @return the Seam integration resource loader
     * @throws DeploymentUnitProcessingException for any error
     */
    protected synchronized ResourceRoot getSeamIntResourceRoot() throws DeploymentUnitProcessingException {
        try {
            if (seamIntResourceRoot == null) {
                final ModuleLoader moduleLoader = Module.getBootModuleLoader();
                Module extModule = moduleLoader.loadModule(EXT_CONTENT_MODULE);
                URL url = extModule.getExportedResource(SEAM_INT_JAR);
                if (url == null)
                    throw ServerLogger.ROOT_LOGGER.noSeamIntegrationJarPresent(extModule);
                File file = new File(url.toURI());
                ResourceLoader loader = ResourceLoaders.newResourceLoader(SEAM_INT_JAR, file, false);
                seamIntResourceRoot = new ResourceRoot(loader);
            }
            return seamIntResourceRoot;
        } catch (Exception e) {
            throw new DeploymentUnitProcessingException(e);
        }
    }

    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        if (deploymentUnit.getParent() != null) {
            return;
        }

        final List<DeploymentUnit> deploymentUnits = new ArrayList<>();
        deploymentUnits.add(deploymentUnit);
        deploymentUnits.addAll(deploymentUnit.getAttachmentList(Attachments.SUB_DEPLOYMENTS));

        for (DeploymentUnit unit : deploymentUnits) {
            final ResourceRoot mainRoot = unit.getAttachment(Attachments.DEPLOYMENT_ROOT);
            if (mainRoot == null) continue;
            final ResourceLoader root = mainRoot.getLoader();
            for (final String path : SEAM_FILES) {
                if (root.getResource(path) != null) {
                    final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
                    try {
                        moduleSpecification.addResourceLoader(ResourceLoaderSpec.createResourceLoaderSpec(getSeamIntResourceRoot().getLoader()));
                    } catch (Exception e) {
                        throw new DeploymentUnitProcessingException(e);
                    }
                    unit.addToAttachmentList(Attachments.RESOURCE_ROOTS, getSeamIntResourceRoot());
                    return;
                }
            }
        }
    }

    public void undeploy(final DeploymentUnit context) {
    }
}
