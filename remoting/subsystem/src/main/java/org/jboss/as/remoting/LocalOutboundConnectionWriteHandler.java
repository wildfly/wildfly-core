/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

/**
 * @author Jaikiran Pai
 */
class LocalOutboundConnectionWriteHandler extends AbstractWriteAttributeHandler<Boolean> {

    static final LocalOutboundConnectionWriteHandler INSTANCE = new LocalOutboundConnectionWriteHandler();

    private LocalOutboundConnectionWriteHandler() {
        super(LocalOutboundConnectionResourceDefinition.OUTBOUND_SOCKET_BINDING_REF);
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Boolean> handbackHolder) throws OperationFailedException {
        boolean handback = applyModelToRuntime(context, operation);
        handbackHolder.setHandback(handback);
        return handback;
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Boolean handback) throws OperationFailedException {
        if (handback != null && !handback.booleanValue()) {
            final ModelNode restored = Resource.Tools.readModel(context.readResource(PathAddress.EMPTY_ADDRESS));
            restored.get(attributeName).set(valueToRestore);
            applyModelToRuntime(context, restored);
        } // else we didn't update the runtime in applyUpdateToRuntime
    }

    private boolean applyModelToRuntime(OperationContext context, ModelNode fullModel) throws OperationFailedException {
        boolean reloadRequired = false;
        final String connectionName = context.getCurrentAddressValue();
        final ServiceName serviceName = LocalOutboundConnectionResourceDefinition.OUTBOUND_CONNECTION_CAPABILITY.getCapabilityServiceName(connectionName);
        final ServiceRegistry registry = context.getServiceRegistry(true);
        ServiceController sc = registry.getService(serviceName);
        if (sc != null && sc.getState() == ServiceController.State.UP) {
            reloadRequired = true;
        } else {
            // Service isn't up so we can bounce it
            context.removeService(serviceName); // safe even if the service doesn't exist
            // install the service with new values
            LocalOutboundConnectionAdd.INSTANCE.installRuntimeService(context, fullModel);
        }
        return reloadRequired;
    }
}
