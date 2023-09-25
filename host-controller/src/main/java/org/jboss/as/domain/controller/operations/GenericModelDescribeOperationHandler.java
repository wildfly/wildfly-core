/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.controller.operations;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_ORGANIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.operations.PathAddressFilter;
import org.jboss.as.controller.operations.common.OrderedChildTypesAttachment;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * A generic model "describe" handler, returning a list of operations which is needed to create an equivalent model.
 *
 * @author Emanuel Muckenhuber
 */
public class GenericModelDescribeOperationHandler implements OperationStepHandler {

    public static final GenericModelDescribeOperationHandler INSTANCE = new GenericModelDescribeOperationHandler("describe-model", false);

    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder("describe-model", ControllerResolver.getResolver(SUBSYSTEM))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.READ_WHOLE_CONFIG)
            .setReplyType(ModelType.LIST)
            .setReplyValueType(ModelType.OBJECT)
            .setPrivateEntry()
            .build();

    private static final Set<String> ROOT_ATTRIBUTES = new HashSet<>(Arrays.asList(DOMAIN_ORGANIZATION));
    private final String operationName;
    private final boolean skipLocalAdd;
    protected GenericModelDescribeOperationHandler(final String operationName, final boolean skipAdd) {
        this.operationName = operationName;
        this.skipLocalAdd = skipAdd;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final PathAddress address = PathAddress.pathAddress(operation.require(ModelDescriptionConstants.OP_ADDR));
        final PathAddressFilter filter = context.getAttachment(PathAddressFilter.KEY);
        if (filter != null && ! filter.accepts(address)) {
            return;
        }
        final ImmutableManagementResourceRegistration registration = context.getResourceRegistration();
        if (registration.isAlias() || registration.isRemote() || registration.isRuntimeOnly()) {
            return;
        }
        final Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS, false);
        final ModelNode result = context.getResult();
        result.setEmptyList();
        final ModelNode results = new ModelNode().setEmptyList();
        final AtomicReference<ModelNode> failureRef = new AtomicReference<ModelNode>();
        final Map<String, ModelNode> includeResults = new HashMap<String, ModelNode>();

        // Step to handle failed operations
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                boolean failed = false;
                if (failureRef.get() != null) {
                    // One of our subsystems failed
                    context.getFailureDescription().set(failureRef.get());
                    failed = true;
                } else {
                    for (final ModelNode includeRsp : includeResults.values()) {
                        if (includeRsp.hasDefined(FAILURE_DESCRIPTION)) {
                            context.getFailureDescription().set(includeRsp.get(FAILURE_DESCRIPTION));
                            failed = true;
                            break;
                        }
                        final ModelNode includeResult = includeRsp.get(RESULT);
                        if (includeResult.isDefined()) {
                            for (ModelNode op : includeResult.asList()) {
                                addOrderedChildTypeInfo(context, resource, op);
                                result.add(op);
                            }
                        }
                    }
                }
                if (!failed) {
                    for (final ModelNode childRsp : results.asList()) {
                        addOrderedChildTypeInfo(context, resource, childRsp);
                        result.add(childRsp);
                    }
                    context.getResult().set(result);
                }
            }
        }, OperationContext.Stage.MODEL, true);

        final Set<String> children = resource.getChildTypes();
        for (final String childType : children) {
            for (final Resource.ResourceEntry entry : resource.getChildren(childType)) {

                final PathElement childPE = entry.getPathElement();
                final PathAddress relativeChildAddress = PathAddress.EMPTY_ADDRESS.append(childPE);
                final ImmutableManagementResourceRegistration childRegistration = registration.getSubModel(relativeChildAddress);

                if (childRegistration.isRuntimeOnly()
                        || childRegistration.isRemote()
                        || childRegistration.isAlias()) {

                    continue;
                }

                final PathAddress absoluteChildAddr = address.append(childPE);
                // Skip ignored addresses
                if (filter != null && !filter.accepts(absoluteChildAddr)) {
                    continue;
                }

                final OperationStepHandler stepHandler = childRegistration.getOperationHandler(PathAddress.EMPTY_ADDRESS, operationName);
                final ModelNode childRsp = new ModelNode();

                context.addStep(new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        if (failureRef.get() == null) {
                            if (childRsp.hasDefined(FAILURE_DESCRIPTION)) {
                                failureRef.set(childRsp.get(FAILURE_DESCRIPTION));
                            } else if (childRsp.hasDefined(RESULT)) {
                                addChildOperation(address, childRsp.require(RESULT).asList(), results);
                            }
                        }
                    }
                }, OperationContext.Stage.MODEL, true);

                final ModelNode childOperation = operation.clone();
                childOperation.get(ModelDescriptionConstants.OP).set(operationName);
                childOperation.get(ModelDescriptionConstants.OP_ADDR).set(absoluteChildAddr.toModelNode());
                context.addStep(childRsp, childOperation, stepHandler, OperationContext.Stage.MODEL, true);
            }
        }

        if (resource.isProxy() || resource.isRuntime()) {
            return;
        }

        // Generic operation generation
        final ModelNode model = resource.getModel();
        final OperationStepHandler addHandler = registration.getOperationHandler(PathAddress.EMPTY_ADDRESS, ModelDescriptionConstants.ADD);
        if (addHandler != null) {

            final ModelNode add = new ModelNode();
            add.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.ADD);
            add.get(ModelDescriptionConstants.OP_ADDR).set(address.toModelNode());
            final Set<String> attributes = registration.getAttributeNames(PathAddress.EMPTY_ADDRESS);
            for (final String attribute : attributes) {
                if (!model.hasDefined(attribute)) {
                    continue;
                }

                // Process attributes
                final AttributeAccess attributeAccess = registration.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attribute);
                if (attributeAccess.getStorageType() == AttributeAccess.Storage.CONFIGURATION) {
                    add.get(attribute).set(model.get(attribute));
                }
            }

            // Allow the profile describe handler to process profile includes
            processMore(context, operation, resource, address, includeResults);
            if (!skipLocalAdd) {
                addOrderedChildTypeInfo(context, resource, add);
                result.add(add);
            }

        } else {
            // Create write attribute operations
            final Set<String> attributes = registration.getAttributeNames(PathAddress.EMPTY_ADDRESS);
            for (final String attribute : attributes) {
                if (!model.hasDefined(attribute)) {
                    continue;
                }
                if (address.size() == 0 && !ROOT_ATTRIBUTES.contains(attribute)) {
                    continue;
                }
                // Process attributes
                final AttributeAccess attributeAccess = registration.getAttributeAccess(PathAddress.EMPTY_ADDRESS, attribute);
                if (attributeAccess.getStorageType() == AttributeAccess.Storage.CONFIGURATION) {
                    final ModelNode writeAttribute = new ModelNode();
                    writeAttribute.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION);
                    writeAttribute.get(ModelDescriptionConstants.OP_ADDR).set(address.toModelNode());

                    writeAttribute.get(NAME).set(attribute);
                    writeAttribute.get(VALUE).set(model.get(attribute));
                    addOrderedChildTypeInfo(context, resource, writeAttribute);
                    result.add(writeAttribute);
                }
            }
        }
    }

    private void addOrderedChildTypeInfo(OperationContext context, Resource resource, ModelNode operation) {
        OrderedChildTypesAttachment attachment = context.getAttachment(OrderedChildTypesAttachment.KEY);
        if (attachment != null) {
            attachment.addOrderedChildResourceTypes(PathAddress.pathAddress(operation.get(OP_ADDR)), resource);
        }
    }

    protected void addChildOperation(final PathAddress parent, final List<ModelNode> operations, ModelNode results) {
        for (final ModelNode operation : operations) {
            results.add(operation);
        }
    }

    protected void processMore(final OperationContext context, final ModelNode operation, final Resource resource, final PathAddress address, final Map<String, ModelNode> includeResults) throws OperationFailedException {

    }

}
