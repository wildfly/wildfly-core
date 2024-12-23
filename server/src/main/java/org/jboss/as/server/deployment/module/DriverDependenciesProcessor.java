/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import java.util.List;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;
import org.jboss.vfs.VirtualFile;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DriverDependenciesProcessor implements DeploymentUnitProcessor {
    private static final String SERVICE_FILE_NAME = "META-INF/services/java.sql.Driver";
    private static final String JTA = "jakarta.transaction.api";

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        final ModuleLoader moduleLoader = Module.getBootModuleLoader();
        if (deploymentUnit.hasAttachment(Attachments.RESOURCE_ROOTS)) {
            final List<ResourceRoot> resourceRoots = deploymentUnit.getAttachmentList(Attachments.RESOURCE_ROOTS);
            for (ResourceRoot root : resourceRoots) {
                VirtualFile child = root.getRoot().getChild(SERVICE_FILE_NAME);
                if (child.exists()) {
                    moduleSpecification.addSystemDependency(ModuleDependency.Builder.of(moduleLoader, JTA).build());
                    break;
                }
            }
        }
    }

}
