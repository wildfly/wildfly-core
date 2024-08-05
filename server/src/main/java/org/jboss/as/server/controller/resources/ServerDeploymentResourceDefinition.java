/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.controller.resources;

import java.util.function.Supplier;

import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry.Flag;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.deployment.DeploymentListModulesHandler;
import org.jboss.as.server.deployment.DeploymentStatus;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.ExplodedDeploymentAddContentHandler;
import org.jboss.as.server.deployment.DeploymentAddHandler;
import org.jboss.as.server.deployment.DeploymentDeployHandler;
import org.jboss.as.server.deployment.DeploymentExplodeHandler;
import org.jboss.as.server.deployment.DeploymentRedeployHandler;
import org.jboss.as.server.deployment.DeploymentRemoveHandler;
import org.jboss.as.server.deployment.DeploymentUndeployHandler;
import org.jboss.as.server.deployment.ManagedDeploymentBrowseContentHandler;
import org.jboss.as.server.deployment.ManagedDeploymentReadContentHandler;
import org.jboss.as.server.deployment.ExplodedDeploymentRemoveContentHandler;
import org.wildfly.service.capture.ServiceValueExecutorRegistry;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ServerDeploymentResourceDefinition extends DeploymentResourceDefinition {

    private final ContentRepository contentRepository;
    private final ServerEnvironment serverEnvironment;
    private final ServiceValueExecutorRegistry<DeploymentUnit> deploymentUnitRegistry;
    private final ServiceValueExecutorRegistry<Supplier<DeploymentStatus>> statusRegistry;

    private ServerDeploymentResourceDefinition(ContentRepository contentRepository,
                                               ServerEnvironment serverEnvironment, OperationStepHandler addHandler,
                                               OperationStepHandler removeHandler,
                                               ServiceValueExecutorRegistry<DeploymentUnit> deploymentUnitRegistry,
                                               ServiceValueExecutorRegistry<Supplier<DeploymentStatus>> statusRegistry) {
        super(DeploymentResourceParent.SERVER, addHandler, removeHandler, statusRegistry);
        this.contentRepository = contentRepository;
        this.serverEnvironment = serverEnvironment;
        this.deploymentUnitRegistry = deploymentUnitRegistry;
        this.statusRegistry = statusRegistry;
    }

    public static ServerDeploymentResourceDefinition create(final ContentRepository contentRepository,
                                                            final ServerEnvironment serverEnvironment,
                                                            final ServiceValueExecutorRegistry<DeploymentUnit> deploymentUnitRegistry,
                                                            final ServiceValueExecutorRegistry<Supplier<DeploymentStatus>> statusRegistry) {
        return new ServerDeploymentResourceDefinition(contentRepository, serverEnvironment,
                DeploymentAddHandler.create(contentRepository, deploymentUnitRegistry, statusRegistry),
                new DeploymentRemoveHandler(contentRepository, deploymentUnitRegistry, statusRegistry),
                deploymentUnitRegistry, statusRegistry);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(DeploymentAttributes.DEPLOY_DEFINITION, new DeploymentDeployHandler(deploymentUnitRegistry, statusRegistry));
        resourceRegistration.registerOperationHandler(DeploymentAttributes.UNDEPLOY_DEFINITION, new DeploymentUndeployHandler(deploymentUnitRegistry, statusRegistry));
        resourceRegistration.registerOperationHandler(DeploymentAttributes.REDEPLOY_DEFINITION, new DeploymentRedeployHandler(deploymentUnitRegistry, statusRegistry));
        resourceRegistration.registerOperationHandler(DeploymentAttributes.EXPLODE_DEFINITION, new DeploymentExplodeHandler(contentRepository));
        resourceRegistration.registerOperationHandler(DeploymentAttributes.DEPLOYMENT_ADD_CONTENT_DEFINITION, new ExplodedDeploymentAddContentHandler(contentRepository, serverEnvironment));
        resourceRegistration.registerOperationHandler(DeploymentAttributes.DEPLOYMENT_REMOVE_CONTENT_DEFINITION, new ExplodedDeploymentRemoveContentHandler(contentRepository, serverEnvironment));
        resourceRegistration.registerOperationHandler(DeploymentAttributes.DEPLOYMENT_READ_CONTENT_DEFINITION, new ManagedDeploymentReadContentHandler(contentRepository));
        resourceRegistration.registerOperationHandler(DeploymentAttributes.DEPLOYMENT_BROWSE_CONTENT_DEFINITION, new ManagedDeploymentBrowseContentHandler(contentRepository));
        resourceRegistration.registerOperationHandler(DeploymentAttributes.LIST_MODULES, new DeploymentListModulesHandler(deploymentUnitRegistry));
    }

    @Override
    protected void registerAddOperation(ManagementResourceRegistration registration, OperationStepHandler handler, Flag... flags) {
        registration.registerOperationHandler(DeploymentAttributes.SERVER_DEPLOYMENT_ADD_DEFINITION, handler);
    }
}
