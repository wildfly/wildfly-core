/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.operation;

import java.util.Set;

import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityReferenceRecorder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

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
    protected boolean requiresRuntime(OperationContext context) {
        return super.requiresRuntime(context) && this.descriptor.getRuntimeHandler().isPresent();
    }

    @Override
    protected void performRemove(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        if (removeInCurrentStep(resource)) {
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
        }

        super.performRemove(context, operation, model);
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

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        Set<OperationEntry.Flag> flags = context.getResourceRegistration().getOperationFlags(PathAddress.EMPTY_ADDRESS, context.getCurrentOperationName());
        if (flags.contains(OperationEntry.Flag.RESTART_NONE) || (flags.contains(OperationEntry.Flag.RESTART_RESOURCE_SERVICES) && context.isResourceServiceRestartAllowed())) {
            ResourceOperationRuntimeHandler handler = this.descriptor.getRuntimeHandler().orElse(null);
            if (handler != null) {
                handler.removeRuntime(context, model);
            }
        } else if (flags.contains(OperationEntry.Flag.RESTART_JVM)) {
            context.restartRequired();
        } else {
            context.reloadRequired();
        }
    }

    @Override
    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        Set<OperationEntry.Flag> flags = context.getResourceRegistration().getOperationFlags(PathAddress.EMPTY_ADDRESS, context.getCurrentOperationName());
        if (flags.contains(OperationEntry.Flag.RESTART_NONE) || (flags.contains(OperationEntry.Flag.RESTART_RESOURCE_SERVICES) && context.isResourceServiceRestartAllowed())) {
            ResourceOperationRuntimeHandler handler = this.descriptor.getRuntimeHandler().orElse(null);
            if (handler != null) {
                handler.addRuntime(context, model);
            }
        } else if (flags.contains(OperationEntry.Flag.RESTART_JVM)) {
            context.revertRestartRequired();
        } else {
            context.revertReloadRequired();
        }
    }

    @Override
    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        // We already unregistered our capabilities in performRemove(...)
    }
}
