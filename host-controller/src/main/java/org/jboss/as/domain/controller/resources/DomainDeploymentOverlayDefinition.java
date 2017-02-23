/*
 * Copyright 2017 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
