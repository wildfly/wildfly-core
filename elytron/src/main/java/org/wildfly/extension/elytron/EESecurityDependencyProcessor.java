/*
 * Copyright 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;

/**
 * A simple {@link DeploymentUnitProcessor} that adds the 'javax.security.auth.message.api' and 'javax.security.jacc.api'
 * modules to the deployment.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
class EESecurityDependencyProcessor implements DeploymentUnitProcessor {

    public static final ModuleIdentifier AUTH_MESSAGE_API = ModuleIdentifier.create("javax.security.auth.message.api");
    public static final ModuleIdentifier JACC_API = ModuleIdentifier.create("javax.security.jacc.api");

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ModuleLoader moduleLoader = Module.getBootModuleLoader();
        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);

        moduleSpecification.addSystemDependency(new ModuleDependency(moduleLoader, JACC_API, false, false, true, false));
        moduleSpecification.addSystemDependency(new ModuleDependency(moduleLoader, AUTH_MESSAGE_API, false, false, true, false));
    }

    @Override
    public void undeploy(DeploymentUnit context) {}

}
