/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.operation;

import java.util.Objects;
import java.util.Set;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityReferenceRecorder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.wildfly.service.capture.ValueRegistry;

/**
 * Generic remove operation step handler that delegates service removal/recovery to a dedicated {@link ResourceOperationRuntimeHandler}.
 * @author Paul Ferraro
 */
public class RemoveResourceOperationStepHandler extends AbstractRemoveStepHandler {

    private final OperationStepHandlerDescriptor descriptor;

    public RemoveResourceOperationStepHandler(OperationStepHandlerDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        // Can be null if resource will be removed by a subsequent step
        Resource resource = this.removeResource(context, operation);

        if (this.requiresRuntime(context, resource)) {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    RemoveResourceOperationStepHandler.this.performRuntime(context, operation, resource);
                    context.completeStep(new OperationContext.RollbackHandler() {
                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            try {
                                RemoveResourceOperationStepHandler.this.recoverServices(context, operation, resource);
                            } catch (OperationFailedException e) {
                                ControllerLogger.MGMT_OP_LOGGER.errorRevertingOperation(e, this.getClass().getSimpleName(), operation.require(ModelDescriptionConstants.OP).asString(), context.getCurrentAddress());
                            }
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    boolean requiresRuntime(OperationContext context, Resource resource) {
        // Require runtime if a runtime handler exists, the resource provides its own runtime service, or a value registry exists for any attribute.
        return (resource != null) && this.requiresRuntime(context) && (this.descriptor.getRuntimeHandler().isPresent() || (resource instanceof ResourceOperationRuntimeHandler) || context.getResourceRegistration().getAttributes(PathAddress.EMPTY_ADDRESS).values().stream().map(AttributeAccess::getAttributeDefinition).map(this.descriptor::getValueRegistry).anyMatch(Objects::nonNull));
    }

    /**
     * Returns the removed resource, or null, if resource will removed by a subsequent step
     * @param context the operation context
     * @param operation the operation
     * @return the removed resource, or null, if resource will removed by a subsequent step
     * @throws OperationFailedException
     */
    private Resource removeResource(OperationContext context, ModelNode operation) throws OperationFailedException {
        Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        ModelNode model = resource.getModel();

        if (!removeInCurrentStep(resource)) {
            // Add steps for recursive removal
            this.performRemove(context, operation, model);
            return null;
        }

        // We need to remove capabilities *before* removing the resource, since the capability reference resolution might involve reading the resource
        PathAddress address = context.getCurrentAddress();
        ImmutableManagementResourceRegistration registration = context.getResourceRegistration();

        for (RuntimeCapability<?> capability : registration.getCapabilities()) {
            // Only register capabilities when allowed by the associated predicate
            if (this.descriptor.getCapabilityFilter(capability).test(context, resource)) {
                context.deregisterCapability((capability.isDynamicallyNamed() ? capability.fromBaseCapability(address) : capability).getName());
            }
        }

        for (AttributeAccess attribute : registration.getAttributes(PathAddress.EMPTY_ADDRESS).values()) {
            // Skip runtime attributes and aliases
            if (AttributeAccess.Storage.RUNTIME.test(attribute) || AttributeAccess.Flag.ALIAS.test(attribute)) continue;

            AttributeDefinition definition = attribute.getAttributeDefinition();
            String attributeName = definition.getName();
            if (definition.hasCapabilityRequirements()) {
                definition.removeCapabilityRequirements(context, resource, model.get(attributeName));
            }
        }

        for (CapabilityReferenceRecorder recorder : registration.getRequirements()) {
            recorder.removeCapabilityRequirements(context, resource, null);
        }

        // Remove resource
        return context.removeResource(PathAddress.EMPTY_ADDRESS);
    }

    /*
     * Determines whether resource removal happens in this step, or a subsequent step
     */
    private static boolean removeInCurrentStep(Resource resource) {
        for (String childType : resource.getChildTypes()) {
            for (Resource.ResourceEntry entry : resource.getChildren(childType)) {
                if (!entry.isRuntime() && resource.hasChild(entry.getPathElement())) {
                    return false;
                }
            }
        }
        return true;
    }

    void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        ImmutableManagementResourceRegistration registration = context.getResourceRegistration();
        Set<OperationEntry.Flag> flags = registration.getOperationFlags(PathAddress.EMPTY_ADDRESS, context.getCurrentOperationName());
        if (flags.contains(OperationEntry.Flag.RESTART_NONE) || (flags.contains(OperationEntry.Flag.RESTART_RESOURCE_SERVICES) && context.isResourceServiceRestartAllowed())) {
            ResourceOperationRuntimeHandler handler = this.descriptor.getRuntimeHandler().orElse(null);
            if (handler != null) {
                handler.removeRuntime(context, resource);
            }
            if (resource instanceof ResourceOperationRuntimeHandler resourceHandler) {
                resourceHandler.removeRuntime(context, resource);
            }
            PathAddress address = context.getCurrentAddress();
            for (AttributeAccess attribute : registration.getAttributes(PathAddress.EMPTY_ADDRESS).values()) {
                ValueRegistry<PathAddress, ModelNode> registry = this.descriptor.getValueRegistry(attribute.getAttributeDefinition());
                if (registry != null) {
                    registry.remove(address);
                }
            }
        } else if (flags.contains(OperationEntry.Flag.RESTART_JVM)) {
            context.restartRequired();
        } else {
            context.reloadRequired();
        }
    }

    void recoverServices(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        ImmutableManagementResourceRegistration registration = context.getResourceRegistration();
        Set<OperationEntry.Flag> flags = registration.getOperationFlags(PathAddress.EMPTY_ADDRESS, context.getCurrentOperationName());
        if (flags.contains(OperationEntry.Flag.RESTART_NONE) || (flags.contains(OperationEntry.Flag.RESTART_RESOURCE_SERVICES) && context.isResourceServiceRestartAllowed())) {
            PathAddress address = context.getCurrentAddress();
            ModelNode model = resource.getModel();
            for (AttributeAccess attribute : registration.getAttributes(PathAddress.EMPTY_ADDRESS).values()) {
                ValueRegistry<PathAddress, ModelNode> registry = this.descriptor.getValueRegistry(attribute.getAttributeDefinition());
                if (registry != null) {
                    registry.add(address).accept(attribute.getAttributeDefinition().resolveModelAttribute(context, model));
                }
            }
            if (resource instanceof ResourceOperationRuntimeHandler resourceHandler) {
                resourceHandler.addRuntime(context, resource);
            }
            ResourceOperationRuntimeHandler handler = this.descriptor.getRuntimeHandler().orElse(null);
            if (handler != null) {
                handler.addRuntime(context, resource);
            }
        } else if (flags.contains(OperationEntry.Flag.RESTART_JVM)) {
            context.revertRestartRequired();
        } else {
            context.revertReloadRequired();
        }
    }

    @Override
    protected final void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) {
        // Already performed prior to resource removal
        throw new IllegalStateException();
    }

    @Override
    protected final void performRuntime(OperationContext context, ModelNode operation, ModelNode model) {
        // Not invoked
        throw new IllegalStateException();
    }

    @Override
    protected final void recoverServices(OperationContext context, ModelNode operation, ModelNode model) {
        // Not invoked
        throw new IllegalStateException();
    }
}
