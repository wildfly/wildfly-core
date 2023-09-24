/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_MODEL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;

import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.operations.common.OrderedChildTypesAttachment;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.Transformers;
import org.jboss.as.domain.controller.operations.deployment.SyncModelParameters;
import org.jboss.as.host.controller.mgmt.HostControllerRegistrationHandler;
import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 */
abstract class SyncModelHandlerBase implements OperationStepHandler {

    // Create a local transformer for now, maybe just bypass transformation and only worry about the ignored resources
    private static final Transformers TRANSFORMERS = Transformers.Factory.createLocal();
    private static final ModelNode OPERATION = new ModelNode();

    static {
        OPERATION.get(OP).set("sync");
        OPERATION.get(OP_ADDR).setEmptyList();
    }

    private final SyncModelParameters parameters;

    protected SyncModelHandlerBase(SyncModelParameters parameters) {
        this.parameters = parameters;
    }

    abstract Transformers.ResourceIgnoredTransformationRegistry createRegistry(OperationContext context, Resource remoteModel, Set<String> remoteExtensions);

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        // Create the remote model based on the result of the read-master-model operation
        final Set<String> remoteExtensions = new HashSet<>();
        final Resource remote = ReadMasterDomainModelUtil.createResourceFromDomainModelOp(operation.require(DOMAIN_MODEL), remoteExtensions);
        final Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry = createRegistry(context, remote, remoteExtensions);

        // Describe the local model
        final ReadDomainModelHandler readModelHandler = new ReadDomainModelHandler(ignoredTransformationRegistry, TRANSFORMERS, true);
        final HostControllerRegistrationHandler.OperationExecutor operationExecutor = parameters.getOperationExecutor();
        final ModelNode localModel = operationExecutor.executeReadOnly(OPERATION, readModelHandler, ModelController.OperationTransactionControl.COMMIT);
        if (localModel.hasDefined(FAILURE_DESCRIPTION)) {
            context.getFailureDescription().set(localModel.get(FAILURE_DESCRIPTION));
            return;
        }

        // Translate the local domain-model to a resource
        final Set<String> localExtensions = new HashSet<>();
        final Resource transformedResource = ReadMasterDomainModelUtil.createResourceFromDomainModelOp(localModel.get(RESULT), localExtensions);

        // Create the local describe operations
        final OrderedChildTypesAttachment orderedChildTypesAttachment = new OrderedChildTypesAttachment();
        final ReadMasterDomainOperationsHandler readOperationHandler = new ReadMasterDomainOperationsHandler();
        final ModelNode localOperations = operationExecutor.executeReadOnly(OPERATION, transformedResource, readOperationHandler, ModelController.OperationTransactionControl.COMMIT);
        if (localOperations.hasDefined(FAILURE_DESCRIPTION)) {
            context.getFailureDescription().set(localOperations.get(FAILURE_DESCRIPTION));
            return;
        }

        // Determine the extensions we are missing locally
        for (final String extension : localExtensions) {
            remoteExtensions.remove(extension);
        }

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                final ModelNode result = localOperations.get(RESULT);
                final SyncModelOperationHandler handler =
                        new SyncModelOperationHandler(result.asList(), remote, remoteExtensions,
                                parameters, readOperationHandler.getOrderedChildTypes());
                context.addStep(operation, handler, OperationContext.Stage.MODEL, true);
            }
        }, OperationContext.Stage.MODEL, true);
    }

    protected SyncModelParameters getParameters() {
        return parameters;
    }
}
