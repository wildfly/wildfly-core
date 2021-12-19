/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron.expression;

import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;

/**
 * Attaches a function to the {@link DeploymentUnit} that can be used to resolve credential store expressions
 * in deployment resources.
 */
public final class DeploymentExpressionResolverProcessor implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        CapabilityServiceSupport serviceSupport = deploymentUnit.getAttachment(Attachments.CAPABILITY_SERVICE_SUPPORT);
        try {
            final ElytronExpressionResolver resolver = serviceSupport.getCapabilityRuntimeAPI("org.wildfly.security.expression-resolver", ElytronExpressionResolver.class);
            deploymentUnit.addToAttachmentList(Attachments.DEPLOYMENT_EXPRESSION_RESOLVERS, (s) -> resolver.resolveDeploymentExpression(s, serviceSupport));
        } catch (CapabilityServiceSupport.NoSuchCapabilityException e) {
            // /subsystem=elytron/expression=encryption resource is not present, so there's nothing to do.
        }
    }

    @Override
    public void undeploy(DeploymentUnit context) {

    }
}
