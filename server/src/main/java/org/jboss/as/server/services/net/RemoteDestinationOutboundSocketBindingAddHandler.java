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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.server.services.net.OutboundSocketBindingResourceDefinition.OUTBOUND_SOCKET_BINDING_CAPABILITY;

import java.net.UnknownHostException;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * Handles "add" operation for remote-destination outbound-socket-binding
 *
 * @author Jaikiran Pai
 */
public class RemoteDestinationOutboundSocketBindingAddHandler extends AbstractAddStepHandler {

    static final RemoteDestinationOutboundSocketBindingAddHandler INSTANCE = new RemoteDestinationOutboundSocketBindingAddHandler();

    private RemoteDestinationOutboundSocketBindingAddHandler() {
        super(OutboundSocketBindingResourceDefinition.OUTBOUND_SOCKET_BINDING_CAPABILITY, RemoteDestinationOutboundSocketBindingResourceDefinition.ATTRIBUTES);
    }

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {

        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        final String outboundSocketName = address.getLastElement().getValue();
        try {
            installOutboundSocketBindingService(context, model, outboundSocketName);
        } catch (UnknownHostException e) {
            throw new OperationFailedException(e.toString());
        }
    }

    public static void installOutboundSocketBindingService(final OperationContext context, final ModelNode model,
                                                                                               final String outboundSocketName) throws OperationFailedException, UnknownHostException {
        final ServiceTarget serviceTarget = context.getServiceTarget();

        // destination host
        final String destinationHost = RemoteDestinationOutboundSocketBindingResourceDefinition.HOST.resolveModelAttribute(context, model).asString();
        // port
        final int destinationPort = RemoteDestinationOutboundSocketBindingResourceDefinition.PORT.resolveModelAttribute(context, model).asInt();

        // (optional) source interface
        final ModelNode sourceInterfaceModelNode = OutboundSocketBindingResourceDefinition.SOURCE_INTERFACE.resolveModelAttribute(context, model);
        final String sourceInterfaceName = sourceInterfaceModelNode.isDefined() ? sourceInterfaceModelNode.asString() : null;
        // (optional) source port
        final ModelNode sourcePortModelNode = OutboundSocketBindingResourceDefinition.SOURCE_PORT.resolveModelAttribute(context, model);
        final Integer sourcePort = sourcePortModelNode.isDefined() ? sourcePortModelNode.asInt() : null;
        // (optional) fixedSourcePort
        final ModelNode fixedSourcePortModelNode = OutboundSocketBindingResourceDefinition.FIXED_SOURCE_PORT.resolveModelAttribute(context, model);
        final boolean fixedSourcePort = fixedSourcePortModelNode.isDefined() && fixedSourcePortModelNode.asBoolean();
        // create the service
        final OutboundSocketBindingService outboundSocketBindingService = new RemoteDestinationOutboundSocketBindingService(outboundSocketName, destinationHost, destinationPort, sourcePort, fixedSourcePort);
        final ServiceName serviceName = OUTBOUND_SOCKET_BINDING_CAPABILITY.getCapabilityServiceName(outboundSocketName, OutboundSocketBinding.class);
        final ServiceBuilder<OutboundSocketBinding> serviceBuilder = serviceTarget.addService(serviceName, outboundSocketBindingService);
        // if a source interface has been specified then add a dependency on it
        if (sourceInterfaceName != null) {
            serviceBuilder.addDependency(NetworkInterfaceService.JBOSS_NETWORK_INTERFACE.append(sourceInterfaceName), NetworkInterfaceBinding.class, outboundSocketBindingService.getSourceNetworkInterfaceBindingInjector());
        }
        serviceBuilder.addDependency(SocketBindingManager.SOCKET_BINDING_MANAGER, SocketBindingManager.class, outboundSocketBindingService.getSocketBindingManagerInjector())
                .setInitialMode(ServiceController.Mode.ON_DEMAND)
                .addAliases(OutboundSocketBinding.OUTBOUND_SOCKET_BINDING_BASE_SERVICE_NAME.append(outboundSocketName))
                .install();
    }
}
