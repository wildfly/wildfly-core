/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * Simple remove handler that, if allowed, restarts a parent resource when a child is removed.
 * Otherwise the server is put into a forced reload.
 *
 * @author Jason T. Greene
 */
public abstract class RestartParentResourceHandlerBase implements OperationStepHandler {
    private final String parentKeyName;

    protected RestartParentResourceHandlerBase(String parentKeyName) {
        this.parentKeyName = parentKeyName;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        // Do the simple model part
        updateModel(context, operation);

        if (!context.isBooting() && requiresRuntime(context)) {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

                    PathAddress address = getParentAddress(context.getCurrentAddress());
                    ServiceName serviceName = getParentServiceName(address);
                    final ServiceController<?> service = serviceName != null ?
                            context.getServiceRegistry(false).getService(serviceName) : null;

                    boolean servicesRestarted = false;
                    final boolean reloadRequired = service != null && !isResourceServiceRestartAllowed(context, service);
                    ModelNode parentModel = reloadRequired || (service != null) ? getModel(context, address) : null;
                    if (reloadRequired) {
                        if (parentModel != null) {
                            context.reloadRequired();
                        } // else the parent remove must have run as part of this op and we're not responsible for runtime
                    } else if (service != null ) {
                        if (parentModel != null && context.markResourceRestarted(address, RestartParentResourceHandlerBase.this)) {
                            // Remove/recreate parent services in separate step using an OperationContext relative to the parent resource
                            // This is necessary to support service installation via CapabilityServiceTarget
                            // (We use Util.getReadResourceOperation(address) just to get an op with the desired address;
                            //  we're not doing a read-resource.)
                            context.addStep(Util.getReadResourceOperation(address), new OperationStepHandler() {
                                @Override
                                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                                    removeServices(context, serviceName, parentModel);
                                    recreateParentService(context, parentModel);
                                }
                            }, OperationContext.Stage.RUNTIME, true);
                            servicesRestarted = true;
                        }
                    } // else  No parent service, nothing to do

                    // If we restarted services, keep the model that drove the new services so we can
                    // revert the change on rollback
                    final ModelNode invalidatedParentModel = servicesRestarted ? parentModel : null;

                    context.completeStep(new OperationContext.RollbackHandler() {
                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            Resource resource = context.readResource(PathAddress.EMPTY_ADDRESS);
                            rollbackRuntime(context, operation, resource);
                            if (reloadRequired) {
                                context.revertReloadRequired();
                            } else if (invalidatedParentModel != null) {
                                recoverServices(context, invalidatedParentModel);
                            }
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);
        }
    }

    /**
     * Gets whether this operation needs to update the runtime. The default implementation returns {@code true}
     * if {@link OperationContext#getProcessType()#isHostController()} is false.
     *
     * @param context the operation context
     * @return {@code true} if the operation should update the runtime; {@code false} if it only updates the configuration
     *         model
     */
    protected boolean requiresRuntime(OperationContext context) {
        return !context.getProcessType().isHostController();
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
     * Performs the update to the persistent configuration model.
     *
     * @param context the operation context
     * @param operation  the operation
     * @throws OperationFailedException if there is a problem updating the model
     */
    protected abstract void updateModel(final OperationContext context, final ModelNode operation) throws OperationFailedException;

    /**
     * Rollback the update.
     *
     * @param context the operation context
     * @param operation  the operation
     * @param resource the resource
     */
    protected void rollbackRuntime(final OperationContext context, final ModelNode operation, final Resource resource) {
        // no-op
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
     * @deprecated Use {@link #recreateParentService(OperationContext, ModelNode) instead.
     */
    @Deprecated(forRemoval = true)
    protected void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel) throws OperationFailedException {
    }

    /**
     * Gets the name of the parent service.
     *
     * @param parentAddress the address of the parent resource
     * @return  the service name
     */
    protected abstract ServiceName getParentServiceName(PathAddress parentAddress);


    protected PathAddress getParentAddress(PathAddress address) {
        return Util.getParentAddressByKey(address, parentKeyName);
    }

    private void recoverServices(final OperationContext context, final ModelNode invalidatedParentModel) {
        PathAddress address = getParentAddress(context.getCurrentAddress());
        ServiceName serviceName = getParentServiceName(address);

        ModelNode parentModel = getOriginalModel(context, address);
        if (parentModel != null && context.revertResourceRestarted(address, this)) {
            // Remove/recreate parent services in separate step using an OperationContext relative to the parent resource
            // This is necessary to support service installation via CapabilityServiceTarget
            // (We use Util.getReadResourceOperation(address) just to get an op with the desired address;
            //  we're not doing a read-resource.)
            context.addStep(Util.getReadResourceOperation(address), new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    removeServices(context, serviceName, invalidatedParentModel);
                    recreateParentService(context, parentModel);
                }
            }, Stage.RUNTIME, true);
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
