/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.controller.resources;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBDEPLOYMENT;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.server.deployment.DeploymentListModulesHandler;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.wildfly.service.capture.ServiceValueExecutorRegistry;

/**
 * The sub-deployment resource definition.
 *
 * @author Yeray Borges
 */
public class ServerSubDeploymentResourceDefinition extends SimpleResourceDefinition {

    private final ServiceValueExecutorRegistry<DeploymentUnit> deploymentUnitRegistry;

    private ServerSubDeploymentResourceDefinition(final PathElement pathElement, final ResourceDescriptionResolver descriptionResolver,
                                                  final ServiceValueExecutorRegistry<DeploymentUnit> deploymentUnitRegistry) {
        super(pathElement, descriptionResolver);
        this.deploymentUnitRegistry = deploymentUnitRegistry;
    }

    static ServerSubDeploymentResourceDefinition create(final ServiceValueExecutorRegistry<DeploymentUnit> deploymentUnitRegistry){
        return new ServerSubDeploymentResourceDefinition(PathElement.pathElement(SUBDEPLOYMENT), DeploymentAttributes.DEPLOYMENT_RESOLVER, deploymentUnitRegistry);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(DeploymentAttributes.LIST_MODULES, new DeploymentListModulesHandler(deploymentUnitRegistry));
    }
}
