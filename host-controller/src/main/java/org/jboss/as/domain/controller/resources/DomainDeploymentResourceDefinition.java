/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.domain.controller.resources;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.CONTENT_RESOURCE_ALL;
import static org.jboss.as.server.controller.resources.DeploymentAttributes.isUnmanagedContent;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry.Flag;
import org.jboss.as.domain.controller.operations.deployment.DeploymentAddHandler;
import org.jboss.as.domain.controller.operations.deployment.DeploymentExplodeHandler;
import org.jboss.as.domain.controller.operations.deployment.DeploymentRemoveHandler;
import org.jboss.as.domain.controller.operations.deployment.ExplodedDeploymentAddContentHandler;
import org.jboss.as.domain.controller.operations.deployment.ExplodedDeploymentRemoveContentHandler;
import org.jboss.as.domain.controller.operations.deployment.ManagedDeploymentBrowseContentHandler;
import org.jboss.as.domain.controller.operations.deployment.ManagedDeploymentReadContentHandler;
import org.jboss.as.domain.controller.operations.deployment.ServerGroupDeploymentAddHandler;
import org.jboss.as.domain.controller.operations.deployment.ServerGroupDeploymentDeployHandler;
import org.jboss.as.domain.controller.operations.deployment.ServerGroupDeploymentRedeployHandler;
import org.jboss.as.domain.controller.operations.deployment.ServerGroupDeploymentRemoveHandler;
import org.jboss.as.domain.controller.operations.deployment.ServerGroupDeploymentUndeployHandler;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.HostFileRepository;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.as.server.controller.resources.DeploymentResourceDefinition;
import org.jboss.dmr.ModelNode;

class DomainDeploymentResourceDefinition extends DeploymentResourceDefinition {

    private final OperationDefinition addDefinition;
    private final OperationStepHandler explodeDeploymentHandler;
    private final OperationStepHandler explodedDeploymentAddContentHandler;
    private final OperationStepHandler explodedDeploymentRemoveContentHandler;
    private final OperationStepHandler explodedDeploymentReadContentHandler;
    private final OperationStepHandler explodedDeploymentBrowseContentHandler;

    private DomainDeploymentResourceDefinition(DeploymentResourceParent parent,
            OperationDefinition addDefinition, OperationStepHandler addHandler, OperationStepHandler removeHandler,
            OperationStepHandler explodeDeploymentHandler, OperationStepHandler explodedDeploymentAddContentHandler,
            OperationStepHandler explodedDeploymentRemoveContentHandler, OperationStepHandler explodedDeploymentReadContentHandler,
            OperationStepHandler explodedDeploymentBrowseContentHandler) {
        super(parent, addHandler, removeHandler, null);
        this.addDefinition = addDefinition;
        this.explodeDeploymentHandler = explodeDeploymentHandler;
        this.explodedDeploymentAddContentHandler = explodedDeploymentAddContentHandler;
        this.explodedDeploymentRemoveContentHandler = explodedDeploymentRemoveContentHandler;
        this.explodedDeploymentReadContentHandler = explodedDeploymentReadContentHandler;
        this.explodedDeploymentBrowseContentHandler = explodedDeploymentBrowseContentHandler;
    }

    public static DomainDeploymentResourceDefinition createForDomainMaster(ContentRepository contentRepository) {
        return new DomainDeploymentResourceDefinition(DeploymentResourceParent.DOMAIN,
                DeploymentAttributes.DOMAIN_DEPLOYMENT_ADD_DEFINITION,
                new DeploymentAddHandler(contentRepository),
                DeploymentRemoveHandler.createForMaster(contentRepository),
                new DeploymentExplodeHandler(contentRepository),
                new ExplodedDeploymentAddContentHandler(contentRepository),
                new ExplodedDeploymentRemoveContentHandler(contentRepository),
                new ManagedDeploymentReadContentHandler(contentRepository),
                new ManagedDeploymentBrowseContentHandler(contentRepository));
    }

    public static DomainDeploymentResourceDefinition createForDomainSlave(boolean backupDC, HostFileRepository fileRepository, ContentRepository contentRepository) {
        return new DomainDeploymentResourceDefinition(DeploymentResourceParent.DOMAIN,
                DeploymentAttributes.DOMAIN_DEPLOYMENT_ADD_DEFINITION,
                backupDC ? new DeploymentAddHandler(fileRepository, contentRepository) : new DeploymentAddHandler(null, null),
                DeploymentRemoveHandler.createForSlave(fileRepository, contentRepository),
                new DeploymentExplodeHandler(backupDC, fileRepository, contentRepository),
                new ExplodedDeploymentAddContentHandler(backupDC, fileRepository, contentRepository),
                new ExplodedDeploymentRemoveContentHandler(backupDC, fileRepository, contentRepository),
                null,
                null);
    }

    public static ServerGroupDomainDeploymentResourceDefinition createForServerGroup(HostFileRepository fileRepository,
            ContentRepository contentRepository) {
        return new ServerGroupDomainDeploymentResourceDefinition(DeploymentResourceParent.SERVER_GROUP,
                DeploymentAttributes.SERVER_GROUP_DEPLOYMENT_ADD_DEFINITION,
                new ServerGroupDeploymentAddHandler(fileRepository, contentRepository),
                new ServerGroupDeploymentRemoveHandler(contentRepository), null, null, null, null, null);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        if (getParent() == DeploymentResourceParent.SERVER_GROUP) {
            resourceRegistration.registerOperationHandler(DeploymentAttributes.DEPLOY_DEFINITION, ServerGroupDeploymentDeployHandler.INSTANCE);
            resourceRegistration.registerOperationHandler(DeploymentAttributes.REDEPLOY_DEFINITION, ServerGroupDeploymentRedeployHandler.INSTANCE);
            resourceRegistration.registerOperationHandler(DeploymentAttributes.UNDEPLOY_DEFINITION, ServerGroupDeploymentUndeployHandler.INSTANCE);
        } else {
            resourceRegistration.registerOperationHandler(DeploymentAttributes.EXPLODE_DEFINITION, explodeDeploymentHandler);
            resourceRegistration.registerOperationHandler(DeploymentAttributes.DEPLOYMENT_ADD_CONTENT_DEFINITION, explodedDeploymentAddContentHandler);
            resourceRegistration.registerOperationHandler(DeploymentAttributes.DEPLOYMENT_REMOVE_CONTENT_DEFINITION, explodedDeploymentRemoveContentHandler);
            if (explodedDeploymentReadContentHandler != null) {
                resourceRegistration.registerOperationHandler(DeploymentAttributes.DEPLOYMENT_READ_CONTENT_DEFINITION, explodedDeploymentReadContentHandler);
            }
            if (explodedDeploymentBrowseContentHandler != null) {
                resourceRegistration.registerOperationHandler(DeploymentAttributes.DEPLOYMENT_BROWSE_CONTENT_DEFINITION, explodedDeploymentBrowseContentHandler);
            }
        }
    }

     @Override
    protected void registerAddOperation(ManagementResourceRegistration registration, OperationStepHandler handler, Flag... flags) {
        registration.registerOperationHandler(addDefinition, handler);
    }

    private static class ServerGroupDomainDeploymentResourceDefinition extends DomainDeploymentResourceDefinition {
         ServerGroupDomainDeploymentResourceDefinition(DeploymentResourceParent parent, OperationDefinition addDefinition,
                 OperationStepHandler addHandler, OperationStepHandler removeHandler,
                 OperationStepHandler explodeDeploymentHandler, OperationStepHandler explodedDeploymentAddContentHandler,
                 OperationStepHandler explodedDeploymentRemoveContentHandler,
                 OperationStepHandler explodedDeploymentReadContentHandler,
                 OperationStepHandler explodedDeploymentBrowseContentHandler) {
             super(parent, addDefinition, addHandler, removeHandler, explodeDeploymentHandler,
                     explodedDeploymentAddContentHandler, explodedDeploymentRemoveContentHandler,
                     explodedDeploymentReadContentHandler, explodedDeploymentBrowseContentHandler);
         }

         @Override
         public void extractedManaged(OperationContext context, ModelNode operation) {
              String name = context.getCurrentAddressValue();
              ModelNode deployment = context.readResourceFromRoot(PathAddress.pathAddress(PathElement.pathElement(DEPLOYMENT, name))).getModel();
              if (deployment.hasDefined(CONTENT_RESOURCE_ALL.getName())) {
                  ModelNode content = deployment.get(CONTENT_RESOURCE_ALL.getName()).asList().get(0);
                  context.getResult().set(!isUnmanagedContent(content));
              }
         }
     }
}
