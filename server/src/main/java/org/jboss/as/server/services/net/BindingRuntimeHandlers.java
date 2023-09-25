/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.services.net;

import static org.jboss.as.server.services.net.SocketBindingResourceDefinition.SOCKET_BINDING_CAPABILITY;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.network.ManagedBinding;
import org.jboss.as.network.NetworkUtils;
import org.jboss.as.network.SocketBinding;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * {@code SocketBinding} runtime handlers.
 *
 * @author Emanuel Muckenhuber
 */
public final class BindingRuntimeHandlers {

    abstract static class AbstractBindingRuntimeHandler implements OperationStepHandler {

        /** {@inheritDoc} */
        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
                    final ModelNode result = context.getResult();
                    final String name = context.getCurrentAddressValue();
                    ServiceName svcName = SOCKET_BINDING_CAPABILITY.getCapabilityServiceName(name, SocketBinding.class);
                    final ServiceController<?> controller = context.getServiceRegistry(false).getRequiredService(svcName);
                    if(controller != null && controller.getState() == ServiceController.State.UP) {
                        final SocketBinding binding = SocketBinding.class.cast(controller.getValue());
                        AbstractBindingRuntimeHandler.this.execute(operation, binding, result);
                    } else {
                        result.set(getNoMetrics());
                    }
                }
            }, OperationContext.Stage.RUNTIME);
        }

        abstract void execute(ModelNode operation, SocketBinding binding, ModelNode result);

        abstract ModelNode getNoMetrics();
    }

    public static class BoundHandler extends AbstractBindingRuntimeHandler {

        public static final String ATTRIBUTE_NAME = "bound";
        public static final AttributeDefinition ATTRIBUTE_DEFINITION = SimpleAttributeDefinitionBuilder.create(ATTRIBUTE_NAME, ModelType.BOOLEAN)
                .setRequired(false)
                .setStorageRuntime()
                .setRuntimeServiceNotRequired()
                .build();
        public static final OperationStepHandler INSTANCE = new BoundHandler();

        private BoundHandler() {
            //
        }

        @Override
        void execute(final ModelNode operation, final SocketBinding binding, final ModelNode result) {
            // The socket should be bound when it's registered at the SocketBindingManager
            result.set(binding.isBound());
        }

        ModelNode getNoMetrics() {
            return ModelNode.FALSE;
        }

    }

    public static class BoundAddressHandler extends AbstractBindingRuntimeHandler {

        public static final String ATTRIBUTE_NAME = "bound-address";
        public static final AttributeDefinition ATTRIBUTE_DEFINITION = SimpleAttributeDefinitionBuilder.create(ATTRIBUTE_NAME, ModelType.STRING)
                .setRequired(false)
                .setStorageRuntime()
                .setRuntimeServiceNotRequired()
                .build();
        public static final OperationStepHandler INSTANCE = new BoundAddressHandler();

        private BoundAddressHandler() {
            //
        }

        @Override
        void execute(final ModelNode operation, final SocketBinding binding, final ModelNode result) {
            ManagedBinding managedBinding = binding.getManagedBinding();
            if (managedBinding != null) {
                InetSocketAddress bindAddress = managedBinding.getBindAddress();
                if (bindAddress != null) {
                    InetAddress addr = bindAddress.getAddress();
                    result.set(NetworkUtils.canonize(addr.getHostAddress()));
                }
            }
        }

        ModelNode getNoMetrics() {
            return new ModelNode();
        }
    }

    public static class BoundPortHandler extends AbstractBindingRuntimeHandler {

        public static final String ATTRIBUTE_NAME = "bound-port";
        public static final AttributeDefinition ATTRIBUTE_DEFINITION = SimpleAttributeDefinitionBuilder.create(ATTRIBUTE_NAME, ModelType.INT)
                .setRequired(false)
                .setValidator(new IntRangeValidator(1, Integer.MAX_VALUE, true, false))
                .setStorageRuntime()
                .setRuntimeServiceNotRequired()
                .build();

        public static final OperationStepHandler INSTANCE = new BoundPortHandler();

        private BoundPortHandler() {
            //
        }

        @Override
        void execute(final ModelNode operation, final SocketBinding binding, final ModelNode result) {
            ManagedBinding managedBinding = binding.getManagedBinding();
            if (managedBinding != null) {
                InetSocketAddress bindAddress = managedBinding.getBindAddress();
                if (bindAddress != null) {
                    int port = bindAddress.getPort();
                    result.set(port);
                }
            }
        }

        ModelNode getNoMetrics() {
            return new ModelNode();
        }
    }

    private BindingRuntimeHandlers() {
        //
    }

}
