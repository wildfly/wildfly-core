/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deployment.module;

import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;

/**
 * DUP that adds dependencies that are available to all deployments by default.
 *
 * @author Stuart Douglas
 * @author Thomas.Diesler@jboss.com
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ServerDependenciesProcessor implements DeploymentUnitProcessor {

    private static final String[] DEFAULT_MODULES = {
            "java.se",
            // Currently this is required for Spring deployments. Spring identifies the resource protocol as "vfs" and
            // attempts to use VFS to search for configuration files within the deployment.
            "org.jboss.vfs",
    };

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        final ModuleLoader moduleLoader = Module.getBootModuleLoader();
        for (String moduleName : DEFAULT_MODULES) {
            try {
                moduleLoader.loadModule(moduleName);
                moduleSpecification.addSystemDependency(ModuleDependency.Builder.of(moduleLoader, moduleName).build());
            } catch (ModuleLoadException ex) {
                ServerLogger.ROOT_LOGGER.debugf("Module not found: %s", moduleName);
            }
        }
    }

}
