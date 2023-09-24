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
class GenericOutboundConnectionWriteHandler extends AbstractWriteAttributeHandler<Void> {

    static final GenericOutboundConnectionWriteHandler INSTANCE = new GenericOutboundConnectionWriteHandler();

    private GenericOutboundConnectionWriteHandler() {
        super(GenericOutboundConnectionResourceDefinition.URI);
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode resolvedValue, ModelNode currentValue, HandbackHolder<Void> voidHandbackHolder) throws OperationFailedException {
        final ModelNode fullModel = Resource.Tools.readModel(context.readResource(PathAddress.EMPTY_ADDRESS));
        applyModelToRuntime(context, attributeName, fullModel);

        return false;

    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName, ModelNode valueToRestore, ModelNode valueToRevert, Void handback) throws OperationFailedException {
        final ModelNode restored = Resource.Tools.readModel(context.readResource(PathAddress.EMPTY_ADDRESS));
        restored.get(attributeName).set(valueToRestore);
        applyModelToRuntime(context, attributeName, restored);
    }

    private void applyModelToRuntime(OperationContext context, String attributeName, ModelNode fullModel) throws OperationFailedException {

        final String connectionName = context.getCurrentAddressValue();
        final ServiceName serviceName = GenericOutboundConnectionResourceDefinition.OUTBOUND_CONNECTION_CAPABILITY.getCapabilityServiceName(connectionName);
        final ServiceRegistry registry = context.getServiceRegistry(true);
        ServiceController sc = registry.getService(serviceName);
        if (sc != null && sc.getState() == ServiceController.State.UP) {
            GenericOutboundConnectionService svc = GenericOutboundConnectionService.class.cast(sc.getValue());
            if (GenericOutboundConnectionResourceDefinition.URI.getName().equals(attributeName)) {
                svc.setDestination(GenericOutboundConnectionAdd.INSTANCE.getDestinationURI(context, fullModel));
            }
        } else {
            // Service isn't up so we can bounce it
            context.removeService(serviceName); // safe even if the service doesn't exist
            // install the service with new values
            GenericOutboundConnectionAdd.INSTANCE.installRuntimeService(context, fullModel);
        }
    }
}
