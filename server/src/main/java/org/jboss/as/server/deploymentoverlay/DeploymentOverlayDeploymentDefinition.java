/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.deploymentoverlay;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY;
import static org.jboss.as.server.deploymentoverlay.DeploymentOverlayModel.DEPLOYMENT_OVERRIDE_DEPLOYMENT_PATH;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * Links a deployment overlay to a deployment
 *
 * @author Stuart Douglas
 */
public class DeploymentOverlayDeploymentDefinition extends SimpleResourceDefinition {

    private static final AttributeDefinition[] ATTRIBUTES = {};

    public static AttributeDefinition[] attributes() {
        return ATTRIBUTES.clone();
    }

    public DeploymentOverlayDeploymentDefinition() {
        super(new Parameters(DEPLOYMENT_OVERRIDE_DEPLOYMENT_PATH, ControllerResolver.getResolver(DEPLOYMENT_OVERLAY + '.' + DEPLOYMENT))
                .setAddHandler(new DeploymentOverlayDeploymentAdd()));
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition attr : ATTRIBUTES) {
            resourceRegistration.registerReadOnlyAttribute(attr, null);
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(DeploymentOverlayDeploymentRemoveHandler.REMOVE_DEFINITION, DeploymentOverlayDeploymentRemoveHandler.INSTANCE);
    }
}
