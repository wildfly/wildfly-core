/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.services.net;

import java.net.InetAddress;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.network.NetworkUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * {@code OperationStepHandler} for the runtime attributes of a network interface.
 *
 * @author Emanuel Muckenhuber
 */
public class NetworkInterfaceRuntimeHandler implements OperationStepHandler {

    public static final OperationStepHandler INSTANCE = new NetworkInterfaceRuntimeHandler();

    public static final SimpleAttributeDefinition RESOLVED_ADDRESS = new SimpleAttributeDefinitionBuilder("resolved-address", ModelType.STRING)
            .setStorageRuntime()
            .build();

    protected NetworkInterfaceRuntimeHandler() {
        //
    }

    @Override
    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        final String interfaceName = context.getCurrentAddressValue();
        final String attributeName = operation.require(ModelDescriptionConstants.NAME).asString();
        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                final ServiceName svcName = context.getCapabilityServiceName(NetworkInterfaceBinding.SERVICE_DESCRIPTOR, interfaceName);
                final ServiceController<?> controller = context.getServiceRegistry(false).getService(svcName);
                if(controller != null && controller.getState() == ServiceController.State.UP) {
                    final NetworkInterfaceBinding binding = NetworkInterfaceBinding.class.cast(controller.getValue());
                    final InetAddress address = binding.getAddress();
                    final ModelNode result = new ModelNode();
                    if(RESOLVED_ADDRESS.getName().equals(attributeName)) {
                        result.set(NetworkUtils.canonize(address.getHostAddress()));
                    }
                    context.getResult().set(result);
                }
            }
        }, OperationContext.Stage.RUNTIME);
    }
}
