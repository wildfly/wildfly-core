/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.server.services.net;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.server.services.net.OutboundSocketBindingResourceDefinition.OUTBOUND_SOCKET_BINDING_CAPABILITY;

import org.jboss.as.controller.AbstractWriteAttributeHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;

/**
 * A write attribute handler for handling updates to attributes of a client socket binding.
 * <p/>
 * Any updates to attributes of a client socket binding will trigger a context reload if the {@link OutboundSocketBindingService}
 * corresponding to the binding is already {@link ServiceController.State#UP}. If the service hasn't been started yet,
 * this the updates will not trigger a context reload and instead the {@link OutboundSocketBindingService} will be
 * reinstalled into the service container with the updated values for the attributes
 *
 * @author Jaikiran Pai
 */
class OutboundSocketBindingWriteHandler extends AbstractWriteAttributeHandler<Boolean> {

    private final boolean remoteDestination;

    OutboundSocketBindingWriteHandler(final AttributeDefinition attribute,
                                      final boolean remoteDestination) {
        super(attribute);
        this.remoteDestination = remoteDestination;
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        if (context.getProcessType().isServer()) {
            return super.requiresRuntime(context);
        }
        //Check if we are a host's socket binding and install the service if we are
        PathAddress pathAddress = context.getCurrentAddress();
        return pathAddress.size() > 0 && pathAddress.getElement(0).getKey().equals(HOST);
    }

    @Override
    protected boolean applyUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                           ModelNode resolvedValue, ModelNode currentValue,
                                           HandbackHolder<Boolean> handbackHolder) throws OperationFailedException {
        final String bindingName = context.getCurrentAddressValue();
        final ModelNode bindingModel = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
        final ServiceName serviceName = OUTBOUND_SOCKET_BINDING_CAPABILITY.getCapabilityServiceName(bindingName, OutboundSocketBinding.class);
        final ServiceController<?> controller = context.getServiceRegistry(true).getRequiredService(serviceName);
        final OutboundSocketBinding binding = controller.getState() == ServiceController.State.UP ? OutboundSocketBinding.class.cast(controller.getValue()) : null;
        final boolean bound = binding != null && binding.isConnected(); // FIXME see if this can be used, or remove
        if (binding == null) {
            // existing is not started, so can't "update" it. Instead reinstall the service
            handleBindingReinstall(context, bindingModel, serviceName);
            handbackHolder.setHandback(Boolean.TRUE);
        } else {
            // We don't allow runtime changes without a context reload for outbound socket bindings
            // since any services which might have already injected/depended on the outbound
            // socket binding service would have use the (now stale) attributes.
            context.reloadRequired();
        }

        return false; // we handle the reloadRequired stuff ourselves; it's clearer
    }

    @Override
    protected void revertUpdateToRuntime(OperationContext context, ModelNode operation, String attributeName,
                                         ModelNode valueToRestore, ModelNode valueToRevert, Boolean handback) throws OperationFailedException {
        if (handback != null && handback.booleanValue()) {
            final String bindingName = context.getCurrentAddressValue();
            final ModelNode bindingModel = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
            // Back to the old service
            revertBindingReinstall(context, bindingName, bindingModel, attributeName, valueToRestore);
        } else {
            context.revertReloadRequired();
        }
    }

    private void handleBindingReinstall(OperationContext context, ModelNode bindingModel, ServiceName serviceName) throws OperationFailedException {
        context.removeService(serviceName);
        if (remoteDestination) {
            RemoteDestinationOutboundSocketBindingAddHandler.installOutboundSocketBindingService(context, bindingModel);
        } else {
            LocalDestinationOutboundSocketBindingAddHandler.installOutboundSocketBindingService(context, bindingModel);
        }
    }

    private void revertBindingReinstall(OperationContext context, String bindingName, ModelNode bindingModel,
                                        String attributeName, ModelNode previousValue) {
        final ServiceName serviceName = OUTBOUND_SOCKET_BINDING_CAPABILITY.getCapabilityServiceName(bindingName, OutboundSocketBinding.class);
        context.removeService(serviceName);
        final ModelNode unresolvedConfig = bindingModel.clone();
        unresolvedConfig.get(attributeName).set(previousValue);
        try {
            if (remoteDestination) {
                RemoteDestinationOutboundSocketBindingAddHandler.installOutboundSocketBindingService(context, unresolvedConfig);
            } else {
                LocalDestinationOutboundSocketBindingAddHandler.installOutboundSocketBindingService(context, unresolvedConfig);
            }
        } catch (OperationFailedException e) {
            // Bizarro, as we installed the service before
            throw new RuntimeException(e);
        }
    }

}
