package org.jboss.as.controller;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;

/**
 * Abstract remove step handler that simply removes a service. If the operation is rolled
 * back it delegates the rollback to the corresponding add operations
 * {@link AbstractAddStepHandler#performRuntime(OperationContext, org.jboss.dmr.ModelNode, org.jboss.dmr.ModelNode)}
 * method
 *
 * @author Stuart Douglas
 */
public class ServiceRemoveStepHandler extends AbstractRemoveStepHandler {

    private final ServiceName baseServiceName;
    private final AbstractAddStepHandler addOperation;

    public ServiceRemoveStepHandler(final ServiceName baseServiceName, final AbstractAddStepHandler addOperation, final RuntimeCapability ... unavailableCapabilities) {
        super(unavailableCapabilities);
        this.baseServiceName = baseServiceName;
        this.addOperation = addOperation;
    }

    public ServiceRemoveStepHandler(final ServiceName baseServiceName, final AbstractAddStepHandler addOperation) {
        this.baseServiceName = baseServiceName;
        this.addOperation = addOperation;
    }

    protected ServiceRemoveStepHandler(final AbstractAddStepHandler addOperation, final RuntimeCapability ... unavailableCapabilities) {
        this(null, addOperation, unavailableCapabilities);
    }

    protected ServiceRemoveStepHandler(final AbstractAddStepHandler addOperation) {
        this(null, addOperation);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) {
        if (context.isResourceServiceRestartAllowed()) {
            final PathAddress address = context.getCurrentAddress();
            final String name = address.getLastElement().getValue();
            context.removeService(serviceName(name, address));
        } else {
            context.reloadRequired();
        }
    }

    /**
     * The service name to be removed. Can be overridden for unusual service naming patterns
     * @param name The name of the resource being removed
     * @param address The address of the resource being removed
     * @return The service name to remove
     */
    protected ServiceName serviceName(String name, PathAddress address) {
        return serviceName(name);
    }

    /**
     * The service name to be removed. Can be overridden for unusual service naming patterns
     * @param name The name of the resource being removed
     * @return The service name to remove
     */
    protected ServiceName serviceName(final String name) {
        return baseServiceName.append(name);
    }

    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        if (context.isResourceServiceRestartAllowed()) {
            addOperation.performRuntime(context, operation, model);
        } else {
            context.revertReloadRequired();
        }
    }
}
