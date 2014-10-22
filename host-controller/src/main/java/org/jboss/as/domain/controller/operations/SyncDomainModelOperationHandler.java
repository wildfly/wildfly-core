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

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.host.controller.ignored.IgnoredDomainResourceRegistry;
import org.jboss.as.host.controller.mgmt.HostControllerRegistrationHandler;
import org.jboss.as.host.controller.mgmt.HostInfo;
import org.jboss.dmr.ModelNode;

/**
 * Operation handler synchronizing the domain model. This handler will calculate the operations needed to create
 * the local model and pass them to the {@code SyncModelOperationHandler}.
 *
 * This handler will be called for the initial host registration as well when reconnecting.
 *
 * @author Emanuel Muckenhuber
 */
public class SyncDomainModelOperationHandler implements OperationStepHandler {

    private final HostInfo hostInfo;
    private final ExtensionRegistry extensionRegistry;
    private final IgnoredDomainResourceRegistry ignoredResourceRegistry;
    private final HostControllerRegistrationHandler.OperationExecutor operationExecutor;

    // Create a local transformer for now, maybe just bypass transformation and only worry about the ignored resources
    private static final Transformers TRANSFORMERS = Transformers.Factory.createLocal();

    public SyncDomainModelOperationHandler(HostInfo hostInfo, ExtensionRegistry extensionRegistry, IgnoredDomainResourceRegistry ignoredResourceRegistry, HostControllerRegistrationHandler.OperationExecutor operationExecutor) {
        this.hostInfo = hostInfo;
        this.extensionRegistry = extensionRegistry;
        this.ignoredResourceRegistry = ignoredResourceRegistry;
        this.operationExecutor = operationExecutor;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        try {
            internalExecute(context, operation);
        } catch (Exception e) {
            e.printStackTrace();
            throw new OperationFailedException(e);
        }
    }

    public void internalExecute(OperationContext context, ModelNode operation) throws OperationFailedException {
        context.acquireControllerLock();

        final ModelNode readOp = new ModelNode();
        readOp.get(OP).set(ReadMasterDomainOperationsHandler.OPERATION_NAME);
        readOp.get(OP_ADDR).setEmptyList();

        // Create the remote model based on the result of the read-master-model operation
        final Resource remote = ReadOperationsHandlerUtils.createResourceFromDomainModelOp(operation.require(DOMAIN_MODEL));
        // Create the filter based on the remote model (since it may not be available locally yet).
        final ReadOperationsHandlerUtils.RequiredConfigurationHolder rc = ReadOperationsHandlerUtils.populateHostResolutionContext(hostInfo, remote, extensionRegistry);
        final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry = ReadOperationsHandlerUtils.createHostIgnoredRegistry(hostInfo, rc);

        // Describe the local model
        final ReadDomainModelHandler h1 = new ReadDomainModelHandler(ignoredTransformationRegistry, TRANSFORMERS);
        final ModelNode localModel = operationExecutor.executeReadOnly(readOp, h1, ModelController.OperationTransactionControl.COMMIT);

        // Translate the local domain-model to a resource
        final Resource transformedResource = ReadOperationsHandlerUtils.createResourceFromDomainModelOp(localModel.get(RESULT));

        // Create the local describe operations
        final ReadMasterDomainOperationsHandler h = new ReadMasterDomainOperationsHandler();
        final ModelNode localOperations = operationExecutor.executeReadOnly(readOp, transformedResource, h, ModelController.OperationTransactionControl.COMMIT);

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                final ModelNode result = localOperations.get(RESULT);
                final SyncModelOperationHandler handler = new SyncModelOperationHandler(result.asList(), remote, ignoredResourceRegistry, operationExecutor);
                context.addStep(operation, handler, OperationContext.Stage.MODEL);
                context.stepCompleted();
            }
        }, OperationContext.Stage.MODEL);

        context.stepCompleted();
    }

}
