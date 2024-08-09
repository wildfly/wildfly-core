/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.operation;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityReferenceRecorder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.dmr.ModelNode;
import org.wildfly.subsystem.resource.AttributeTranslation;

/**
 * Generic add operation step handler that delegates service installation/rollback to a {@link ResourceOperationRuntimeHandler}.
 * @author Paul Ferraro
 */
public class AddResourceOperationStepHandler extends AbstractAddStepHandler implements DescribedOperationStepHandler<AddResourceOperationStepHandlerDescriptor> {

    private final AddResourceOperationStepHandlerDescriptor descriptor;

    public AddResourceOperationStepHandler(AddResourceOperationStepHandlerDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return context.isDefaultRequiresRuntime() && (this.descriptor.getRuntimeHandler().isPresent() || this.descriptor.getDeploymentChainContributor().isPresent());
    }

    @Override
    public AddResourceOperationStepHandlerDescriptor getDescriptor() {
        return this.descriptor;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        PathAddress address = context.getCurrentAddress();
        PathAddress parentAddress = address.getParent();
        PathElement path = address.getLastElement();

        OperationStepHandler parentHandler = context.getRootResourceRegistration().getOperationHandler(parentAddress, context.getCurrentOperationName());
        if (parentHandler instanceof AddResourceOperationStepHandlerDescriptor) {
            @SuppressWarnings("unchecked")
            AddResourceOperationStepHandlerDescriptor parentDescriptor = ((DescribedOperationStepHandler<AddResourceOperationStepHandlerDescriptor>) parentHandler).getDescriptor();

            if (parentDescriptor.getRequiredChildResources().containsKey(path)) {
                if (context.readResourceFromRoot(parentAddress, false).hasChild(path)) {
                    // If we are a required child resource of our parent, we need to remove the auto-created resource first
                    context.addStep(Util.createRemoveOperation(address), context.getRootResourceRegistration().getOperationHandler(address, ModelDescriptionConstants.REMOVE), OperationContext.Stage.MODEL);
                    context.addStep(operation, this, OperationContext.Stage.MODEL);
                    return;
                }
            }
            for (PathElement requiredPath : parentDescriptor.getRequiredSingletonChildResources().keySet()) {
                String requiredPathKey = requiredPath.getKey();
                if (requiredPath.getKey().equals(path.getKey())) {
                    Set<String> childrenNames = context.readResourceFromRoot(parentAddress, false).getChildrenNames(requiredPathKey);
                    if (!childrenNames.isEmpty()) {
                        // If there is a required singleton sibling resource, we need to remove it first
                        for (String childName : childrenNames) {
                            PathAddress singletonAddress = parentAddress.append(requiredPathKey, childName);
                            context.addStep(Util.createRemoveOperation(singletonAddress), context.getRootResourceRegistration().getOperationHandler(singletonAddress, ModelDescriptionConstants.REMOVE), OperationContext.Stage.MODEL);
                        }
                        context.addStep(operation, this, OperationContext.Stage.MODEL);
                        return;
                    }
                }
            }
        }

        super.execute(context, operation);
    }

    @Override
    protected Resource createResource(OperationContext context) {
        Resource resource = Resource.Factory.create(context.getResourceRegistration().isRuntimeOnly());
        if (context.isDefaultRequiresRuntime()) {
            resource = this.descriptor.getResourceTransformation().apply(resource);
        }
        context.addResource(PathAddress.EMPTY_ADDRESS, resource);
        return resource;
    }

    @Override
    protected Resource createResource(OperationContext context, ModelNode operation) {
        UnaryOperator<Resource> transformation = context.isDefaultRequiresRuntime() ? this.descriptor.getResourceTransformation() : UnaryOperator.identity();
        ImmutableManagementResourceRegistration registration = context.getResourceRegistration();
        Resource resource = transformation.apply(Resource.Factory.create(registration.isRuntimeOnly(), registration.getOrderedChildTypes()));
        Integer index = registration.isOrderedChildResource() && operation.hasDefined(ModelDescriptionConstants.ADD_INDEX) ? operation.get(ModelDescriptionConstants.ADD_INDEX).asIntOrNull() : null;
        if (index == null) {
            context.addResource(PathAddress.EMPTY_ADDRESS, resource);
        } else {
            context.addResource(PathAddress.EMPTY_ADDRESS, index, resource);
        }
        return resource;
    }

    @Override
    protected void populateModel(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        PathAddress currentAddress = context.getCurrentAddress();
        // Validate and apply attribute translations to operation
        ImmutableManagementResourceRegistration registration = context.getResourceRegistration();
        OperationDefinition operationDefinition = registration.getOperationEntry(PathAddress.EMPTY_ADDRESS, context.getCurrentOperationName()).getOperationDefinition();
        for (AttributeDefinition parameter : operationDefinition.getParameters()) {
            AttributeTranslation translation = this.descriptor.getAttributeTranslation(parameter);
            if (translation != null) {
                String sourceName = parameter.getName();
                if (operation.hasDefined(sourceName)) {
                    ModelNode sourceValue = parameter.validateOperation(operation);
                    ModelNode targetValue = translation.getWriteAttributeOperationTranslator().translate(context, sourceValue);
                    AttributeDefinition targetAttribute = translation.getTargetAttribute();
                    String targetName = targetAttribute.getName();
                    PathAddress targetAddress = translation.getPathAddressTranslator().apply(currentAddress);
                    if (targetAddress == currentAddress) {
                        // If target attribute exists in the current resource, just fix the operation
                        if (!operation.hasDefined(targetName)) {
                            operation.get(targetName).set(targetValue);
                        }
                    } else {
                        // Otherwise, we need a separate write-attribute operation
                        ModelNode writeAttributeOperation = Util.getWriteAttributeOperation(targetAddress, targetAttribute.getName(), targetValue);
                        ImmutableManagementResourceRegistration targetRegistration = (currentAddress == targetAddress) ? registration : context.getRootResourceRegistration().getSubModel(targetAddress);
                        if (targetRegistration == null) {
                            throw new OperationFailedException(ControllerLogger.MGMT_OP_LOGGER.noSuchResourceType(targetAddress));
                        }
                        AttributeAccess targetAttributeAccess = targetRegistration.getAttributeAccess(PathAddress.EMPTY_ADDRESS, targetName);
                        if (targetAttributeAccess == null) {
                            throw new OperationFailedException(ControllerLogger.MGMT_OP_LOGGER.unknownAttribute(targetName));
                        }
                        OperationStepHandler writeAttributeHandler = targetAttributeAccess.getWriteHandler();
                        if (writeAttributeHandler == null) {
                            throw new OperationFailedException(ControllerLogger.MGMT_OP_LOGGER.attributeNotWritable(targetName));
                        }
                        context.addStep(writeAttributeOperation, writeAttributeHandler, OperationContext.Stage.MODEL);
                    }
                }
            }
        }
        // Validate and apply proper attributes to model
        ModelNode model = resource.getModel();
        Map<String, AttributeAccess> attributes = registration.getAttributes(PathAddress.EMPTY_ADDRESS);
        for (AttributeDefinition parameter : operationDefinition.getParameters()) {
            if (this.descriptor.getAttributeTranslation(parameter) == null) {
                AttributeAccess attribute = attributes.get(parameter.getName());
                if ((attribute != null) && AttributeAccess.Storage.CONFIGURATION.test(attribute)) {
                    OperationStepHandler writeHandler = attribute.getWriteHandler();
                    if ((writeHandler != null) && (writeHandler.getClass() != WriteAttributeOperationStepHandler.class)) {
                        // If attribute has custom handling, perform a separate write-attribute operation
                        ModelNode writeAttributeOperation = Util.getWriteAttributeOperation(currentAddress, parameter.getName(), parameter.validateOperation(operation));
                        context.addStep(writeAttributeOperation, writeHandler, OperationContext.Stage.MODEL);
                    } else {
                        // Auto-populate add resource operation parameters that correspond to resource attributes
                        parameter.validateAndSet(operation, model);
                    }
                } else {
                    // Otherwise, just validate parameter
                    parameter.validateOperation(operation);
                }
            }
        }

        // Auto-create required child resources as necessary
        addRequiredChildren(context, this.descriptor.getRequiredChildResources().values(), Resource::hasChild);
        addRequiredChildren(context, this.descriptor.getRequiredSingletonChildResources().values(), AddResourceOperationStepHandler::hasChildren);

        // Don't leave model undefined
        if (!model.isDefined()) {
            model.setEmptyObject();
        }
    }

    private static boolean hasChildren(Resource resource, PathElement path) {
        return resource.hasChildren(path.getKey());
    }

    private static void addRequiredChildren(OperationContext context, Collection<ResourceRegistration> childResources, BiPredicate<Resource, PathElement> present) {
        OperationStepHandler addIfAbsentHandler = new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                PathAddress address = context.getCurrentAddress();
                Resource parentResource = context.readResourceFromRoot(address.getParent(), false);

                if (!present.test(parentResource, address.getLastElement())) {
                    context.getResourceRegistration().getOperationHandler(PathAddress.EMPTY_ADDRESS, ModelDescriptionConstants.ADD).execute(context, operation);
                }
            }
        };
        for (ResourceRegistration childResource : childResources) {
            if (context.enables(childResource)) {
                context.addStep(Util.createAddOperation(context.getCurrentAddress().append(childResource.getPathElement())), addIfAbsentHandler, OperationContext.Stage.MODEL);
            }
        }
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        // Execute deployment chain step, if necessary
        Consumer<DeploymentProcessorTarget> contributor = this.descriptor.getDeploymentChainContributor().orElse(null);
        if (contributor != null) {
            OperationStepHandler deploymentChainStepHandler = new AbstractDeploymentChainStep() {
                @Override
                protected void execute(DeploymentProcessorTarget target) {
                    contributor.accept(target);
                }
            };
            context.addStep(deploymentChainStepHandler, OperationContext.Stage.RUNTIME);
        }
        Set<OperationEntry.Flag> flags = context.getResourceRegistration().getOperationFlags(PathAddress.EMPTY_ADDRESS, context.getCurrentOperationName());
        // Delegate to runtime handler, if we are booting or restart level allows it
        if (context.isBooting() || flags.contains(OperationEntry.Flag.RESTART_NONE) || (flags.contains(OperationEntry.Flag.RESTART_RESOURCE_SERVICES) && context.isResourceServiceRestartAllowed())) {
            ResourceOperationRuntimeHandler handler = this.descriptor.getRuntimeHandler().orElse(null);
            if (handler != null) {
                handler.addRuntime(context, resource);
            }
        } else if (flags.contains(OperationEntry.Flag.RESTART_JVM)) {
            context.restartRequired();
        } else {
            context.reloadRequired();
        }
    }

    @Override
    protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {
        Set<OperationEntry.Flag> flags = context.getResourceRegistration().getOperationFlags(PathAddress.EMPTY_ADDRESS, context.getCurrentOperationName());
        // Delegate to runtime handler, if we are booting or restart level allows it
        if (context.isBooting() || flags.contains(OperationEntry.Flag.RESTART_NONE) || (flags.contains(OperationEntry.Flag.RESTART_RESOURCE_SERVICES) && context.isResourceServiceRestartAllowed())) {
            ResourceOperationRuntimeHandler handler = this.descriptor.getRuntimeHandler().orElse(null);
            if (handler != null) {
                try {
                    handler.removeRuntime(context, resource);
                } catch (OperationFailedException e) {
                    throw new IllegalStateException(e);
                }
            }
        } else if (flags.contains(OperationEntry.Flag.RESTART_JVM)) {
            context.revertRestartRequired();
        } else {
            context.revertReloadRequired();
        }
    }

    @Override
    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        PathAddress address = context.getCurrentAddress();
        ModelNode model = resource.getModel();
        ImmutableManagementResourceRegistration registration = context.getResourceRegistration();

        for (RuntimeCapability<?> capability : registration.getCapabilities()) {
            // Only register capabilities when allowed by the associated predicate
            if (this.descriptor.getCapabilityFilter(capability).test(context, resource)) {
                context.registerCapability(capability.isDynamicallyNamed() ? capability.fromBaseCapability(address) : capability);
            }
        }

        for (AttributeAccess attribute : registration.getAttributes(PathAddress.EMPTY_ADDRESS).values()) {
            // Skip runtime attributes and aliases
            if (AttributeAccess.Storage.RUNTIME.test(attribute) || AttributeAccess.Flag.ALIAS.test(attribute)) continue;

            AttributeDefinition definition = attribute.getAttributeDefinition();
            String attributeName = definition.getName();
            if (definition.hasCapabilityRequirements()) {
                definition.addCapabilityRequirements(context, resource, model.get(attributeName));
            }
        }

        for (CapabilityReferenceRecorder recorder : registration.getRequirements()) {
            recorder.addCapabilityRequirements(context, resource, null);
        }
    }
}
