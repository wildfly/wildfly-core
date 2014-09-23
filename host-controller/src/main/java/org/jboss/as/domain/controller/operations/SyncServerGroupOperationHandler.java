/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_MODEL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.host.controller.IgnoredNonAffectedServerGroupsUtil;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.as.host.controller.mgmt.HostControllerRegistrationHandler;
import org.jboss.dmr.ModelNode;

/**
 * This operation handler is only getting executed on a slave host-controller, synchronizing the model for a
 * single server or server-group, which gets executed when a part of the model is missing. This handler only works on
 * a subset of the domain model and takes the server-group and an optional socket-binding-group parameter into account,
 * filtering out any other information.
 *
 * @author Emanuel Muckenhuber
 */
public class SyncServerGroupOperationHandler implements OperationStepHandler {

    private final ExtensionRegistry extensionRegistry;
    private final IgnoredDomainResourceRegistry ignoredDomainResourceRegistry;
    private final IgnoredNonAffectedServerGroupsUtil.ServerConfigInfo serverConfig;
    private final HostControllerRegistrationHandler.OperationExecutor operationExecutor;

    // Create a local transformer for now, maybe just bypass transformation and only worry about the ignored resources
    private static final Transformers TRANSFORMERS = Transformers.Factory.createLocal();

    public SyncServerGroupOperationHandler(IgnoredNonAffectedServerGroupsUtil.ServerConfigInfo serverConfig, IgnoredDomainResourceRegistry ignoredDomainResourceRegistry,
                                           ExtensionRegistry extensionRegistry, HostControllerRegistrationHandler.OperationExecutor operationExecutor) {
        this.serverConfig = serverConfig;
        this.ignoredDomainResourceRegistry = ignoredDomainResourceRegistry;
        this.extensionRegistry = extensionRegistry;
        this.operationExecutor = operationExecutor;
    }

    @Override
    public void execute(OperationContext context, final ModelNode operation) throws OperationFailedException {

        final ModelNode descibeOp = new ModelNode();
        descibeOp.get(OP).set("sync");
        descibeOp.get(OP_ADDR).setEmptyList();
        descibeOp.get(SERVER).set(serverConfig.toModelNode());


        // Create the remote model based on the result of the read-master-model operation
        final Resource remote = ReadOperationsHandlerUtils.createResourceFromDomainModelOp(operation.require(DOMAIN_MODEL));
        // Create the filter based on the remote model (since it may not be available locally yet).
        final ReadOperationsHandlerUtils.RequiredConfigurationHolder rc = new ReadOperationsHandlerUtils.RequiredConfigurationHolder();
        ReadOperationsHandlerUtils.processServerConfig(remote, rc, serverConfig, extensionRegistry);
        final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry = ReadOperationsHandlerUtils.createServerIgnoredRegistry(rc);

        // Describe the local model
        final ReadDomainModelHandler readModelHandler = new ReadDomainModelHandler(ignoredTransformationRegistry, TRANSFORMERS);
        final ModelNode localModel = operationExecutor.executeReadOnly(descibeOp, readModelHandler, ModelController.OperationTransactionControl.COMMIT);

        // Translate the domain-model to a resource
        final Resource root = ReadOperationsHandlerUtils.createResourceFromDomainModelOp(localModel.get(RESULT));

        // Create the local describe operations
        final ReadMasterDomainOperationsHandler h = new ReadMasterDomainOperationsHandler();
        final ModelNode localOperations = operationExecutor.executeReadOnly(descibeOp, root, h, ModelController.OperationTransactionControl.COMMIT);

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                final ModelNode result = localOperations.get(RESULT);
                final SyncModelOperationHandler handler = new SyncModelOperationHandler(result.asList(), remote, ignoredDomainResourceRegistry, operationExecutor);
                context.addStep(operation, handler, OperationContext.Stage.MODEL);
                context.stepCompleted();
            }
        }, OperationContext.Stage.MODEL);
        context.stepCompleted();
    }

}
