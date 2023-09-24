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
import org.jboss.modules.ClassTransformer;

/**
 * A {@link DeploymentUnitProcessor} that instantiates {@link ClassTransformer}s defined in the
 * <code>jboss-deployment-structure.xml</code> file.
 *
 * @author Marius Bogoevici
 */
public class ClassTransformerProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final DelegatingClassTransformer transformer = deploymentUnit.getAttachment(DelegatingClassTransformer.ATTACHMENT_KEY);
        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        final Module module = deploymentUnit.getAttachment(Attachments.MODULE);
        if (module == null || transformer == null) {
            return;
        }
        try {
            for (String transformerClassName : moduleSpecification.getClassTransformers()) {
                transformer.addTransformer((ClassTransformer) module.getClassLoader().loadClass(transformerClassName).newInstance());
            }
            // activate transformer only after all delegate transformers have been added
            // so that transformers themselves are not instrumented
            transformer.setActive(true);
        } catch (Exception e) {
            throw ServerLogger.ROOT_LOGGER.failedToInstantiateClassTransformer(ClassTransformer.class.getSimpleName(), e);
        }
    }

}
