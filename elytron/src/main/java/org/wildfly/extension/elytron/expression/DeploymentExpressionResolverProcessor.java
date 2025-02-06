/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
            deploymentUnit.addToAttachmentList(Attachments.DEPLOYMENT_EXPRESSION_RESOLVERS, (s) -> resolver.resolveExpression(s, serviceSupport));
        } catch (CapabilityServiceSupport.NoSuchCapabilityException e) {
            // /subsystem=elytron/expression=encryption resource is not present, so there's nothing to do.
        }
    }

    @Override
    public void undeploy(DeploymentUnit context) {

    }
}
