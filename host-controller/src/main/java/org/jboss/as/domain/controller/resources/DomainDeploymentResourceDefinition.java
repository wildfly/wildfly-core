/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.as.domain.controller.resources;

import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry.Flag;
import org.jboss.as.domain.controller.operations.deployment.DeploymentAddHandler;
import org.jboss.as.domain.controller.operations.deployment.DeploymentExplodeHandler;
import org.jboss.as.domain.controller.operations.deployment.DeploymentRemoveHandler;
import org.jboss.as.domain.controller.operations.deployment.ExplodedDeploymentAddContentHandler;
import org.jboss.as.domain.controller.operations.deployment.ManagedDeploymentBrowseContentHandler;
import org.jboss.as.domain.controller.operations.deployment.ManagedDeploymentReadContentHandler;
import org.jboss.as.domain.controller.operations.deployment.ExplodedDeploymentRemoveContentHandler;
import org.jboss.as.domain.controller.operations.deployment.ServerGroupDeploymentAddHandler;
import org.jboss.as.domain.controller.operations.deployment.ServerGroupDeploymentDeployHandler;
import org.jboss.as.domain.controller.operations.deployment.ServerGroupDeploymentRedeployHandler;
import org.jboss.as.domain.controller.operations.deployment.ServerGroupDeploymentRemoveHandler;
import org.jboss.as.domain.controller.operations.deployment.ServerGroupDeploymentUndeployHandler;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.repository.HostFileRepository;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.as.server.controller.resources.DeploymentResourceDefinition;

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
        super(parent, addHandler, removeHandler);
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

    public static DomainDeploymentResourceDefinition createForServerGroup(HostFileRepository fileRepository, ContentRepository contentRepository) {
        return new DomainDeploymentResourceDefinition(DeploymentResourceParent.SERVER_GROUP,
                DeploymentAttributes.SERVER_GROUP_DEPLOYMENT_ADD_DEFINITION,
                new ServerGroupDeploymentAddHandler(fileRepository, contentRepository), new ServerGroupDeploymentRemoveHandler(contentRepository),
                null,
                null,
                null,
                null,
                null);
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

}
