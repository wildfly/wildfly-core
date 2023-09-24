/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import java.util.Collection;

import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * Simple {@link org.jboss.as.controller.AbstractWriteAttributeHandler} that, if allowed,
 * restarts a parent resource when a change is made. Otherwise the server is put into a forced reload.
 *
 * @author Jason T. Greene.
 */
public abstract class RestartParentWriteAttributeHandler extends AbstractWriteAttributeHandler<ModelNode> {
    private final String parentKeyName;

    public RestartParentWriteAttributeHandler(String parentKeyName, AttributeDefinition... definitions) {
        super(definitions);
        this.parentKeyName = parentKeyName;
    }

    public RestartParentWriteAttributeHandler(final String parentKeyName, final Collection<AttributeDefinition> definitions) {
        super(definitions);
        this.parentKeyName = parentKeyName;
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<ModelNode> handbackHolder) throws OperationFailedException {

        PathAddress address = getParentAddress(context.getCurrentAddress());
        ServiceName serviceName = getParentServiceName(address);
        ServiceController<?> service = serviceName != null ?
                             context.getServiceRegistry(false).getService(serviceName) : null;

        // No parent service, nothing to do
        if (service == null) {
            return false;
        }

        boolean restartServices = isResourceServiceRestartAllowed(context, service);

        if (restartServices) {
            ModelNode parentModel = getModel(context, address);
            if (parentModel != null && context.markResourceRestarted(address, this)) {
                // Remove/recreate parent services in separate step using an OperationContext relative to the parent resource
                // This is necessary to support service installation via CapabilityServiceTarget
                context.addStep(Util.getReadResourceOperation(address), new OperationStepHandler() {
                    @Override
                    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                        removeServices(context, serviceName, parentModel);
                        recreateParentService(context, parentModel);
                    }
                }, Stage.RUNTIME);
                handbackHolder.setHandback(parentModel);
            }
        }

        // Fall back to server wide reload
        return !restartServices;
    }

    /**
     * Gets whether a restart of the parent resource's services is allowed. This default implementation
     * checks whether {@link OperationContext#isResourceServiceRestartAllowed() the context allows resource service restarts};
     * subclasses could also check the state of the {@code service}.
     *
     * @param context the operation context
     * @param service the parent service
     * @return {@code true} if a restart is allowed; {@code false}
     */
    protected boolean isResourceServiceRestartAllowed(final OperationContext context, final ServiceController<?> service) {
        return context.isResourceServiceRestartAllowed();
    }

    /**
     * Removes services. This default implementation simply
     * {@link OperationContext#removeService(ServiceController) instructs the context to remove the parentService}.
     * Subclasses could use the provided {@code parentModel} to identify and remove other services.
     *
     * @param context the operation context
     * @param parentService the name of the parent service
     * @param parentModel the model associated with the parent resource, including nodes for any child resources
     *
     * @throws OperationFailedException if there is a problem removing the services
     */
    protected void removeServices(final OperationContext context, final ServiceName parentService, final ModelNode parentModel) throws OperationFailedException {
        context.removeService(parentService);
    }

    /**
     * Recreate the parent service(s) using the given model.
     *
     * @param context the operation context relative to the parent resource
     * @param parentModel the current configuration model for the parent resource and its children
     *
     * @throws OperationFailedException if there is a problem installing the services
     */
    protected void recreateParentService(OperationContext context, ModelNode parentModel) throws OperationFailedException {
        this.recreateParentService(context, context.getCurrentAddress(), parentModel);
    }

    /**
     * Recreate the parent service(s) using the given model.
     *
     * @param context the operation context
     * @param parentAddress the address of the parent resource
     * @param parentModel the current configuration model for the parent resource and its children
     *
     * @throws OperationFailedException if there is a problem installing the services
     * @deprecated Use {@link #recreateParentService(OperationContext, ModelNode) instead
     */
    @Deprecated(forRemoval = true)
    protected void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel) throws OperationFailedException {
    }

    protected abstract ServiceName getParentServiceName(PathAddress parentAddress);

    protected PathAddress getParentAddress(PathAddress address) {
        return Util.getParentAddressByKey(address, parentKeyName);
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode resolvedValue, ModelNode invalidatedParentModel) throws OperationFailedException {

        if (invalidatedParentModel == null) {
            // We didn't restart services, so no need to revert
            return;
        }

        PathAddress address = getParentAddress(context.getCurrentAddress());
        ServiceName serviceName = getParentServiceName(address);

        ModelNode parentModel = getOriginalModel(context, address);
        if (parentModel != null && context.revertResourceRestarted(address, this)) {
            // Remove/recreate parent services in separate step using an OperationContext relative to the parent resource
            // This is necessary to support service installation via CapabilityServiceTarget
            context.addStep(Util.getReadResourceOperation(address), new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    removeServices(context, serviceName, invalidatedParentModel);
                    recreateParentService(context, parentModel);
                }
            }, Stage.RUNTIME);
        }
    }

    private ModelNode getModel(OperationContext ctx, PathAddress address) {
        try {
            Resource resource = ctx.readResourceFromRoot(address);
            return Resource.Tools.readModel(resource);
        } catch (Resource.NoSuchResourceException e) {
            return null;
        }
    }

    private ModelNode getOriginalModel(OperationContext ctx, PathAddress address) {
        try {
            Resource resource = ctx.getOriginalRootResource().navigate(address);
            return Resource.Tools.readModel(resource);
        } catch (Resource.NoSuchResourceException e) {
            return null;
        }
    }
}
