/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.deployments;

import org.jboss.as.logging.LoggingModuleDependency;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;
import org.jboss.modules.ModuleLoader;

/**
 * Adds the default logging dependencies to the deployment.
 * <p/>
 * See {@link LoggingModuleDependency} for defaults.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LoggingDependencyDeploymentProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        final ModuleLoader moduleLoader = Module.getBootModuleLoader();
        // Add the logging modules
        for (LoggingModuleDependency moduleDep : LoggingModuleDependency.values()) {
            final String moduleId = moduleDep.getModuleName();
            try {
                LoggingLogger.ROOT_LOGGER.tracef("Adding module '%s' to deployment '%s'", moduleId, deploymentUnit.getName());
                moduleLoader.loadModule(moduleId);
                moduleSpecification.addSystemDependency(ModuleDependency.Builder.of(moduleLoader, moduleId).setImportServices(moduleDep.isImportServices()).build());
            } catch (ModuleLoadException ex) {
                LoggingLogger.ROOT_LOGGER.debugf("Module not found: %s", moduleId);
            }
        }
    }

}
