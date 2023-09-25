/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.services.net;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.server.services.net.OutboundSocketBindingResourceDefinition.OUTBOUND_SOCKET_BINDING_CAPABILITY;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.CapabilityServiceTarget;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.as.network.SocketBindingManager;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;

/**
 * Handles "add" operation for remote-destination outbound-socket-binding
 *
 * @author Jaikiran Pai
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class RemoteDestinationOutboundSocketBindingAddHandler extends AbstractAddStepHandler {

    static final RemoteDestinationOutboundSocketBindingAddHandler INSTANCE = new RemoteDestinationOutboundSocketBindingAddHandler();

    private RemoteDestinationOutboundSocketBindingAddHandler() {
        super(RemoteDestinationOutboundSocketBindingResourceDefinition.ATTRIBUTES);
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
    protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource)
            throws OperationFailedException {
        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        final String socketBindingGroupName = address.getParent().getLastElement().getValue();
        final String outboundSocketBindingName = address.getLastElement().getValue();

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                Resource resource;
                if (!context.getProcessType().isServer()) {
                    try {
                        resource = context.readResourceFromRoot(context.getCurrentAddress().getParent(), false);
                        validation(socketBindingGroupName, outboundSocketBindingName, resource, true, new ArrayList<String>());
                    } catch (Resource.NoSuchResourceException e) {
                        // this occurs in the case of an ignored server-group being added to a slave.
                        // for all other cases, the parent element is always present.
                        return;
                    }
                } else {
                    resource = context.readResourceFromRoot(PathAddress.pathAddress(ModelDescriptionConstants.SOCKET_BINDING_GROUP, socketBindingGroupName), false);
                    validation(socketBindingGroupName, outboundSocketBindingName, resource, false, new ArrayList<String>());
                }
            }

            private void validation(final String socketBindingGroupName, final String outboundSocketBindingName, final Resource resource, final boolean recursive, List<String> validatedGroupList) {
                Set<String> socketBindingNames = resource.getChildrenNames(ModelDescriptionConstants.SOCKET_BINDING);
                Set<String> localDestinationOutboundSocketBindingNames = resource.getChildrenNames(ModelDescriptionConstants.LOCAL_DESTINATION_OUTBOUND_SOCKET_BINDING);
                if (socketBindingNames.contains(outboundSocketBindingName) || localDestinationOutboundSocketBindingNames.contains(outboundSocketBindingName)) {
                    throw ControllerLogger.ROOT_LOGGER.socketBindingalreadyDeclared(Element.SOCKET_BINDING.getLocalName(),
                            Element.OUTBOUND_SOCKET_BINDING.getLocalName(), outboundSocketBindingName,
                            Element.SOCKET_BINDING_GROUP.getLocalName(), socketBindingGroupName);
                }
                validatedGroupList.add(socketBindingGroupName);

                if (recursive && resource.getModel().hasDefined(ModelDescriptionConstants.INCLUDES)) {
                    List<ModelNode> includedSocketBindingGroups = resource.getModel().get(ModelDescriptionConstants.INCLUDES).asList();
                    for(ModelNode includedSocketBindingGroup : includedSocketBindingGroups){
                        String includedSocketBindingGroupName = includedSocketBindingGroup.asString();
                        if (!validatedGroupList.contains(includedSocketBindingGroupName)) {
                            Resource includedResource = context.readResourceFromRoot(PathAddress.pathAddress(ModelDescriptionConstants.SOCKET_BINDING_GROUP, includedSocketBindingGroupName), false);
                            validation(includedSocketBindingGroupName, outboundSocketBindingName, includedResource, recursive, validatedGroupList);
                        }
                    }
                }
            }
        }, Stage.MODEL);

        super.populateModel(context, operation, resource);
    }

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {

        installOutboundSocketBindingService(context, model);
    }

    static void installOutboundSocketBindingService(final OperationContext context, final ModelNode model) throws OperationFailedException {
        final CapabilityServiceTarget serviceTarget = context.getCapabilityServiceTarget();

        // socket name
        final String outboundSocketName = context.getCurrentAddressValue();
        // destination host
        final String destinationHost = RemoteDestinationOutboundSocketBindingResourceDefinition.HOST.resolveModelAttribute(context, model).asString();
        // port
        final int destinationPort = RemoteDestinationOutboundSocketBindingResourceDefinition.PORT.resolveModelAttribute(context, model).asInt();
        // (optional) source interface
        final String sourceInterfaceName = OutboundSocketBindingResourceDefinition.SOURCE_INTERFACE.resolveModelAttribute(context, model).asStringOrNull();
        // (optional) source port
        final Integer sourcePort = OutboundSocketBindingResourceDefinition.SOURCE_PORT.resolveModelAttribute(context, model).asIntOrNull();
        // (optional but defaulted) fixedSourcePort
        final boolean fixedSourcePort = OutboundSocketBindingResourceDefinition.FIXED_SOURCE_PORT.resolveModelAttribute(context, model).asBoolean();

        // create the service
        final CapabilityServiceBuilder<?> builder = serviceTarget.addCapability(OUTBOUND_SOCKET_BINDING_CAPABILITY);
        final Consumer<OutboundSocketBinding> osbConsumer = builder.provides(OUTBOUND_SOCKET_BINDING_CAPABILITY);
        final Supplier<SocketBindingManager> sbmSupplier = builder.requiresCapability("org.wildfly.management.socket-binding-manager", SocketBindingManager.class);
        final Supplier<NetworkInterfaceBinding> nibSupplier = sourceInterfaceName != null ? builder.requiresCapability("org.wildfly.network.interface", NetworkInterfaceBinding.class, sourceInterfaceName) : null;
        builder.setInstance(new RemoteDestinationOutboundSocketBindingService(osbConsumer, sbmSupplier, nibSupplier, outboundSocketName, destinationHost, destinationPort, sourcePort, fixedSourcePort));
        builder.setInitialMode(ServiceController.Mode.ON_DEMAND);

        builder.addAliases(OUTBOUND_SOCKET_BINDING_CAPABILITY.getCapabilityServiceName((outboundSocketName)));
        builder.install();
    }
}
