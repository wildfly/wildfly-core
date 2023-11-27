/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.services.net;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLIENT_MAPPINGS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DESTINATION_ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.server.services.net.SocketBindingResourceDefinition.SOCKET_BINDING_CAPABILITY;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.CapabilityServiceTarget;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.validation.MaskedAddressValidator;
import org.jboss.as.controller.resource.AbstractSocketBindingResourceDefinition;
import org.jboss.as.network.ClientMapping;
import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.network.SocketBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController.Mode;

/**
 * Handler for the server and host model socket-binding resource's add operation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class BindingAddHandler extends SocketBindingAddHandler {

    public static final BindingAddHandler INSTANCE = new BindingAddHandler();
    private static final InetAddress ANY_IPV6;

    static {
        try {
            ANY_IPV6 = InetAddress.getByAddress(new byte[16]);
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Not possible");
        }
    }

    private BindingAddHandler() {
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {

        PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        String name = address.getLastElement().getValue();

        try {
            installBindingService(context, model, name);
        } catch (UnknownHostException e) {
            throw new OperationFailedException(e.toString());
        }

    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return true;
    }

    static void installBindingService(OperationContext context, ModelNode config, String name)
            throws UnknownHostException, OperationFailedException {
        final CapabilityServiceTarget serviceTarget = context.getCapabilityServiceTarget();

        final ModelNode intfNode = AbstractSocketBindingResourceDefinition.INTERFACE.resolveModelAttribute(context, config);
        final String intf = intfNode.isDefined() ? intfNode.asString() : null;
        final int port = AbstractSocketBindingResourceDefinition.PORT.resolveModelAttribute(context, config).asInt();
        final boolean fixedPort = AbstractSocketBindingResourceDefinition.FIXED_PORT.resolveModelAttribute(context, config).asBoolean();
        final ModelNode mcastNode = AbstractSocketBindingResourceDefinition.MULTICAST_ADDRESS.resolveModelAttribute(context, config);
        final String mcastAddr = mcastNode.isDefined() ? mcastNode.asString() : null;
        final int mcastPort = AbstractSocketBindingResourceDefinition.MULTICAST_PORT.resolveModelAttribute(context, config).asInt(0);
        final InetAddress mcastInet = mcastAddr == null ? null : InetAddress.getByName(mcastAddr);
        final ModelNode mappingsNode = config.get(CLIENT_MAPPINGS);
        final List<ClientMapping> clientMappings = mappingsNode.isDefined() ? parseClientMappings(context, mappingsNode) : null;

        final CapabilityServiceBuilder<?> builder = serviceTarget.addCapability(SOCKET_BINDING_CAPABILITY);
        final Consumer<SocketBinding> sbConsumer = builder.provides(SOCKET_BINDING_CAPABILITY);
        final Supplier<NetworkInterfaceBinding> ibSupplier = intf != null ? builder.requires(NetworkInterfaceBinding.SERVICE_DESCRIPTOR, intf) : null;
        final Supplier<SocketBindingManager> sbSupplier = builder.requires(SocketBindingManager.SERVICE_DESCRIPTOR);
        final SocketBindingService service = new SocketBindingService(sbConsumer, ibSupplier, sbSupplier, name, port, fixedPort, mcastInet, mcastPort, clientMappings);
        builder.setInstance(service);
        builder.addAliases(SOCKET_BINDING_CAPABILITY.getCapabilityServiceName((name)));
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    static List<ClientMapping> parseClientMappings(OperationContext context, ModelNode mappings) throws OperationFailedException {
        List<ClientMapping> clientMappings = new ArrayList<ClientMapping>();
        for (ModelNode mappingNode : mappings.asList()) {
            ModelNode sourceNode = AbstractSocketBindingResourceDefinition.CLIENT_MAPPING_SOURCE_NETWORK.resolveModelAttribute(context, mappingNode);
            final InetAddress sourceAddress;
            final int mask;
            final String destination;
            final int port;
            if (sourceNode.isDefined()) {
                MaskedAddressValidator.ParsedResult parsedResult = MaskedAddressValidator.parseMasked(sourceNode);
                sourceAddress = parsedResult.address;
                mask = parsedResult.mask;
            } else {
                // Client mappings are always communicated in IPv6
                sourceAddress = ANY_IPV6;
                mask = 0;
            }

            ModelNode destinationNode = AbstractSocketBindingResourceDefinition.CLIENT_MAPPING_DESTINATION_ADDRESS.resolveModelAttribute(context, mappingNode);
            if (! destinationNode.isDefined()) {
                // Validation prevents this, but just in case
                throw ControllerLogger.ROOT_LOGGER.nullNotAllowed(DESTINATION_ADDRESS);
            }
            destination = destinationNode.asString();

            ModelNode portNode = AbstractSocketBindingResourceDefinition.CLIENT_MAPPING_DESTINATION_PORT.resolveModelAttribute(context, mappingNode);
            port = portNode.isDefined() ? portNode.asInt() : -1;
            clientMappings.add(new ClientMapping(sourceAddress, mask, destination, port));
        }

        return clientMappings;
    }
}
