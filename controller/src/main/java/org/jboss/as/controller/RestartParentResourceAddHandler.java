/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;



import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTO_POPULATE_MODEL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Simple add handler which, if allowed, restarts a parent resource when the child is added.
 * Otherwise, the server is put into a forced reload.
 *
 * @author Jason T. Greene
 */
public abstract class RestartParentResourceAddHandler extends RestartParentResourceHandlerBase implements OperationDescriptor {

    /**
     * This is only retained to support {@link #getAttributes()} and for those subclasses that reference this protected attribute directly.
     */
    @Deprecated(forRemoval = true) // This is referenceable by subclasses
    protected final Collection<? extends AttributeDefinition> attributes;

    protected RestartParentResourceAddHandler(String parentKeyName) {
        super(parentKeyName);
        this.attributes = List.of();
    }

    @Deprecated(forRemoval = true)
    protected RestartParentResourceAddHandler(String parentKeyName, Collection<? extends AttributeDefinition> attributes) {
        super(parentKeyName);
        this.attributes = attributes.isEmpty() ? List.of() : List.copyOf(attributes);
    }

    @Deprecated(forRemoval = true)
    protected RestartParentResourceAddHandler(String parentKeyName, RuntimeCapability ... capabilities) {
        this(parentKeyName);
    }

    @Deprecated(forRemoval = true)
    public RestartParentResourceAddHandler(String parentKeyName, Set<RuntimeCapability> capabilities, Collection<? extends AttributeDefinition> attributes) {
        this(parentKeyName, attributes);
    }

    /**
     * Returns the attributes with which this handler was constructed.
     * @deprecated Use the {@link OperationDefinition#getParameters()} method of the operation definition with which this handler was registered instead.
     */
    @Override
    @Deprecated(forRemoval = true)
    public Collection<? extends AttributeDefinition> getAttributes() {
        return this.attributes;
    }

    @Override
    protected void updateModel(OperationContext context, ModelNode operation) throws OperationFailedException {
        final Resource resource = context.createResource(PathAddress.EMPTY_ADDRESS);
        populateModel(context, operation, resource);
        recordCapabilitiesAndRequirements(context, operation, resource);
    }

    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        for (RuntimeCapability<?> capability : context.getResourceRegistration().getCapabilities()) {
            if (capability.isDynamicallyNamed()) {
                context.registerCapability(capability.fromBaseCapability(context.getCurrentAddress()));
            } else {
                context.registerCapability(capability);
            }
        }

        ModelNode model = resource.getModel();
        for (AttributeDefinition ad : attributes) {
            if (model.hasDefined(ad.getName()) || ad.hasCapabilityRequirements()) {
                ad.addCapabilityRequirements(context, resource, model.get(ad.getName()));
            }
        }
    }

    /**
     * Populate the given resource in the persistent configuration model based on the values in the given operation.
     * This method isinvoked during {@link org.jboss.as.controller.OperationContext.Stage#MODEL}.
     * <p>
     * This default implementation simply calls {@link #populateModel(ModelNode, org.jboss.dmr.ModelNode)}.
     *
     * @param context the operation context
     * @param operation the operation
     * @param resource the resource that corresponds to the address of {@code operation}
     *
     * @throws OperationFailedException if {@code operation} is invalid or populating the model otherwise fails
     */
    protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws  OperationFailedException {
        populateModel(operation, resource.getModel());

        // Detect operation header written by populateModel(ModelNode, ModelNode)
        // If header exists, then subclass expects us to populate the model
        if (operation.hasDefined(OPERATION_HEADERS, AUTO_POPULATE_MODEL)) {
            ModelNode model = resource.getModel();
            ImmutableManagementResourceRegistration registration = context.getResourceRegistration();
            Map<String, AttributeAccess> attributes = registration.getAttributes(PathAddress.EMPTY_ADDRESS);
            for (AttributeDefinition parameter : registration.getOperationEntry(PathAddress.EMPTY_ADDRESS, ModelDescriptionConstants.ADD).getOperationDefinition().getParameters()) {
                AttributeAccess attribute = attributes.get(parameter.getName());
                if ((attribute != null) && !AttributeAccess.Flag.ALIAS.test(attribute)) {
                    // Auto-populate add resource operation parameters that correspond to resource attributes, omitting aliases
                    parameter.validateAndSet(operation, model);
                } else {
                    // Otherwise, just validate parameter
                    parameter.validateOperation(operation);
                }
            }
            // Remove header added via populateModel(ModelNode, ModelNode)
            operation.get(ModelDescriptionConstants.OPERATION_HEADERS).remove(AUTO_POPULATE_MODEL);
        }
    }

    /**
     * Populate the given node in the persistent configuration model based on the values in the given operation.
     *
     * @param operation the operation
     * @param model persistent configuration model node that corresponds to the address of {@code operation}
     *
     * @throws OperationFailedException if {@code operation} is invalid or populating the model otherwise fails
     */
    protected void populateModel(final ModelNode operation, final ModelNode model) throws OperationFailedException {
        // Previously model auto-population happened here based on attributes provided via constructor
        // If this method was invoked, we know that the subclass expects us to populate the model
        // If so, indicate this via an operation header to be detected by our parent method
        operation.get(OPERATION_HEADERS, AUTO_POPULATE_MODEL).set(true);
    }
}
