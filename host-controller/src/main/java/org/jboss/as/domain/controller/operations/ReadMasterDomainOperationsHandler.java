/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.operations.PathAddressFilter;
import org.jboss.as.controller.operations.common.OrderedChildTypesAttachment;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Handler returning the operations needed to recreate the current model. This handler adds additional functionality
 * to filter specific resources which should be included. This may either be the ignored resources on the slave
 * host-controller, or in general the local host resource.
 *
 * @author Emanuel Muckenhuber
 */
public class ReadMasterDomainOperationsHandler implements OperationStepHandler {

    public static final String OPERATION_NAME = "read-master-domain-operations";

    private static final PathAddressFilter DEFAULT_FILTER = new PathAddressFilter(true);
    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("model-operations", ControllerResolver.getResolver(SUBSYSTEM))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.READ_WHOLE_CONFIG)
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.OBJECT)
            .setPrivateEntry()
            .build();

    static {
        // Ignore the local host element, since it's represented as normal a model and not a proxy
        DEFAULT_FILTER.addReject(PathAddress.pathAddress(PathElement.pathElement(HOST)));
    }

    private final OrderedChildTypesAttachment orderedChildTypesAttachment = new OrderedChildTypesAttachment();

    ReadMasterDomainOperationsHandler() {
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        context.acquireControllerLock();
        context.attach(PathAddressFilter.KEY, DEFAULT_FILTER);
        context.attach(OrderedChildTypesAttachment.KEY, orderedChildTypesAttachment);
        context.addStep(operation, GenericModelDescribeOperationHandler.INSTANCE, OperationContext.Stage.MODEL, true);
    }

    OrderedChildTypesAttachment getOrderedChildTypes() {
        return orderedChildTypesAttachment;
    }
}
