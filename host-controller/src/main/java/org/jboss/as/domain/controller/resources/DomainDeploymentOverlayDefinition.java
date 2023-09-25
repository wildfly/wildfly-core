/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.resources;

import org.jboss.as.domain.controller.operations.DomainDeploymentOverlayRedeployLinksHandler;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.DeploymentFileRepository;
import org.jboss.as.server.deploymentoverlay.DeploymentOverlayDefinition;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class DomainDeploymentOverlayDefinition extends DeploymentOverlayDefinition {

    public DomainDeploymentOverlayDefinition(boolean domainLevel, ContentRepository contentRepo, DeploymentFileRepository fileRepository) {
        super(domainLevel, contentRepo, fileRepository);
        addOperation(DomainDeploymentOverlayRedeployLinksHandler.REDEPLOY_LINKS_DEFINITION,
                new DomainDeploymentOverlayRedeployLinksHandler(domainLevel));
    }
}
