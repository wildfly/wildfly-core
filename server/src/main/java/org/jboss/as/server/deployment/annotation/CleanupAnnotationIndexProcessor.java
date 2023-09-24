/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.annotation;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.DeploymentUtils;
import org.jboss.as.server.deployment.module.ResourceRoot;

/**
 * DUP that removes the Jandex indexes and composite index from the deployment unit to save memory
 * @author Stuart Douglas
 */
public class CleanupAnnotationIndexProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        deploymentUnit.removeAttachment(Attachments.COMPOSITE_ANNOTATION_INDEX);
        for(final ResourceRoot root : DeploymentUtils.allResourceRoots(deploymentUnit)) {
            root.removeAttachment(Attachments.ANNOTATION_INDEX);
        }

    }

}
