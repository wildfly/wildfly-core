/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;

/**
 * A simple {@link DeploymentUnitProcessor} that adds the 'jakarta.security.auth.message.api' and 'jakarta.security.jacc.api'
 * modules to the deployment.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
class EESecurityDependencyProcessor implements DeploymentUnitProcessor {

    public static final String AUTH_MESSAGE_API = "jakarta.security.auth.message.api";
    public static final String JACC_API = "jakarta.security.jacc.api";

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ModuleLoader moduleLoader = Module.getBootModuleLoader();
        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);

        moduleSpecification.addSystemDependency(ModuleDependency.Builder.of(moduleLoader, JACC_API).setImportServices(true).build());
        moduleSpecification.addSystemDependency(ModuleDependency.Builder.of(moduleLoader, AUTH_MESSAGE_API).setImportServices(true).build());
    }

}
