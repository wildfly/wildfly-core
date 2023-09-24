/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.module;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ModuleAliasChecker.MessageContext;
import org.jboss.marshalling.reflect.SerializableClassRegistry;
import org.jboss.modules.Module;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;

/**
 * Deployment unit processor that will extract module dependencies from an archive.
 *
 * @author John E. Bailey
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class ModuleDependencyProcessor implements DeploymentUnitProcessor {

    private static final SerializableClassRegistry REGISTRY;

    static {
        REGISTRY = AccessController.doPrivileged(new PrivilegedAction<SerializableClassRegistry>() {
            public SerializableClassRegistry run() {
                return SerializableClassRegistry.getInstance();
            }
        });
    }

    /**
     * Process the deployment root for module dependency information.
     *
     * @param phaseContext the deployment unit context
     * @throws DeploymentUnitProcessingException
     */
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);

        final List<ModuleDependency> manifestDependencies = deploymentUnit.getAttachmentList(Attachments.MANIFEST_DEPENDENCIES);
        ModuleAliasChecker.checkModuleAliasesForDependencies(manifestDependencies, MessageContext.MANIFEST_CONTEXT, deploymentUnit.getName());
        moduleSpecification.addUserDependencies(manifestDependencies);

        if (deploymentUnit.getParent() != null) {
            // propagate parent manifest dependencies
            final List<ModuleDependency> parentDependencies = deploymentUnit.getParent().getAttachmentList(Attachments.MANIFEST_DEPENDENCIES);
            moduleSpecification.addUserDependencies(parentDependencies);
        }
    }

    public void undeploy(final DeploymentUnit context) {
        final Module module = context.getAttachment(Attachments.MODULE);
        if (module != null) {
            REGISTRY.release(module.getClassLoader());
        }
    }
}
