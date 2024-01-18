/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem.resource.operation;

import java.util.function.BiPredicate;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * A {@link ReloadRequiredWriteAttributeHandler} that supports resource service restarts via a {@link ResourceOperationRuntimeHandler}.
 * @author Paul Ferraro
 */
public class WriteAttributeOperationStepHandler extends ReloadRequiredWriteAttributeHandler {

    private final OperationStepHandlerDescriptor descriptor;

    public WriteAttributeOperationStepHandler(OperationStepHandlerDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return super.requiresRuntime(context) && this.descriptor.getRuntimeHandler().isPresent();
    }

    @Override
    protected void recordCapabilitiesAndRequirements(OperationContext context, AttributeDefinition attribute, ModelNode newValue, ModelNode oldValue) {
        PathAddress address = context.getCurrentAddress();
        Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
        // newValue is already applied to the model
        // Clone resource and fabricate old state
        Resource oldResource = resource.clone();
        oldResource.getModel().get(attribute.getName()).set(oldValue);

        ImmutableManagementResourceRegistration registration = context.getResourceRegistration();
        for (RuntimeCapability<?> capability : registration.getCapabilities()) {
            BiPredicate<OperationContext, Resource> predicate = this.descriptor.getCapabilityFilter(capability);
            boolean registered = predicate.test(context, oldResource);
            boolean shouldRegister = predicate.test(context, resource);

            if (!registered && shouldRegister) {
                // Attribute change enables capability registration
                context.registerCapability(capability.isDynamicallyNamed() ? capability.fromBaseCapability(address) : capability);
            } else if (registered && !shouldRegister) {
                // Attribute change disables capability registration
                context.deregisterCapability((capability.isDynamicallyNamed() ? capability.fromBaseCapability(address) : capability).getName());
            }
        }

        if (attribute.hasCapabilityRequirements()) {
            attribute.removeCapabilityRequirements(context, resource, oldValue);
            attribute.addCapabilityRequirements(context, resource, newValue);
        }
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> handback) throws OperationFailedException {
        // This method is only triggered when runtime handler is present
        ResourceOperationRuntimeHandler handler = this.descriptor.getRuntimeHandler().get();
        boolean updated = super.applyUpdateToRuntime(context, operation, attributeName, resolvedValue, currentValue, handback);
        if (updated) {
            PathAddress address = context.getCurrentAddress();
            AttributeAccess attribute = context.getResourceRegistration().getAttributeAccess(PathAddress.EMPTY_ADDRESS, attributeName);
            if (context.isResourceServiceRestartAllowed() && AttributeAccess.Flag.RESTART_RESOURCE_SERVICES.test(attribute) && context.markResourceRestarted(address, handler)) {
                handler.removeRuntime(context, context.getOriginalRootResource().navigate(context.getCurrentAddress()));
                handler.addRuntime(context, context.readResource(PathAddress.EMPTY_ADDRESS, false));
                // Returning false prevents going into reload required state
                return false;
            }
        }
        return updated;
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode resolvedValue, Void handback) throws OperationFailedException {
        // This method is only triggered when runtime handler is present
        ResourceOperationRuntimeHandler handler = this.descriptor.getRuntimeHandler().get();
        PathAddress address = context.getCurrentAddress();
        AttributeAccess attribute = context.getResourceRegistration().getAttributeAccess(PathAddress.EMPTY_ADDRESS, attributeName);
        if (context.isResourceServiceRestartAllowed() && AttributeAccess.Flag.RESTART_RESOURCE_SERVICES.test(attribute) && context.revertResourceRestarted(address, handler)) {
            handler.removeRuntime(context, context.readResource(PathAddress.EMPTY_ADDRESS, false));
            handler.addRuntime(context, context.getOriginalRootResource().navigate(context.getCurrentAddress()));
        }
    }
}
