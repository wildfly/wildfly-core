/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.services.net;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLIENT_MAPPINGS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FIXED_PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MULTICAST_ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MULTICAST_PORT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PORT;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.OperationContext.Stage;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Handler for the socket-binding resource's add operation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class SocketBindingAddHandler extends AbstractAddStepHandler {

    public static final String OPERATION_NAME = ADD;

    public static ModelNode getOperation(PathAddress address, ModelNode socketBinding) {
        ModelNode op = Util.createAddOperation(address);
        if (socketBinding.get(INTERFACE).isDefined()) {
            op.get(INTERFACE).set(socketBinding.get(INTERFACE));
        }
        if (socketBinding.hasDefined(PORT)) {
            op.get(PORT).set(socketBinding.get(PORT));
        }
        if (socketBinding.hasDefined(FIXED_PORT)) {
            op.get(FIXED_PORT).set(socketBinding.get(FIXED_PORT));
        }
        if (socketBinding.hasDefined(MULTICAST_ADDRESS)) {
            op.get(MULTICAST_ADDRESS).set(socketBinding.get(MULTICAST_ADDRESS));
        }
        if (socketBinding.hasDefined(MULTICAST_PORT)) {
            op.get(MULTICAST_PORT).set(socketBinding.get(MULTICAST_PORT));
        }
        if (socketBinding.hasDefined(CLIENT_MAPPINGS)) {
            op.get(CLIENT_MAPPINGS).set(socketBinding.get(CLIENT_MAPPINGS));
        }
        return op;
    }

    public static final SocketBindingAddHandler INSTANCE = new SocketBindingAddHandler();

    /**
     * Create the SocketBindingAddHandler
     */
    protected SocketBindingAddHandler() {
    }

    @Override
    protected void populateModel(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {

        ModelNode model = resource.getModel();
        model.get(NAME).set(context.getCurrentAddressValue());

        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        final String socketBindingGroupName = address.getParent().getLastElement().getValue();
        final String socketBindingName = address.getLastElement().getValue();

        context.addStep(new OperationStepHandler() {
            @Override
            public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                Resource resource;
                if (!context.getProcessType().isServer()) {
                    try {
                        resource = context.readResourceFromRoot(context.getCurrentAddress().getParent(), false);
                        validation(socketBindingGroupName, socketBindingName, resource, true, new ArrayList<String>());
                    } catch (Resource.NoSuchResourceException e) {
                        // this occurs in the case of an ignored server-group being added to a slave.
                        // for all other cases, the parent element is always present.
                        return;
                    }
                } else {
                    resource = context.readResourceFromRoot(PathAddress.pathAddress(ModelDescriptionConstants.SOCKET_BINDING_GROUP, socketBindingGroupName), false);
                    validation(socketBindingGroupName, socketBindingName, resource, false, new ArrayList<String>());
                }
            }

            private void validation(final String socketBindingGroupName, final String socketBindingName, final Resource resource, final boolean recursive, List<String> validatedGroupList) {
                Set<String> localDestinationOutboundSocketBindingNames = resource.getChildrenNames(ModelDescriptionConstants.LOCAL_DESTINATION_OUTBOUND_SOCKET_BINDING);
                Set<String> remoteDestinationOutboundSocketBindingNames = resource.getChildrenNames(ModelDescriptionConstants.REMOTE_DESTINATION_OUTBOUND_SOCKET_BINDING);
                if(localDestinationOutboundSocketBindingNames.contains(socketBindingName) || remoteDestinationOutboundSocketBindingNames.contains(socketBindingName)){
                    throw ControllerLogger.ROOT_LOGGER.socketBindingalreadyDeclared(Element.SOCKET_BINDING.getLocalName(),
                            Element.OUTBOUND_SOCKET_BINDING.getLocalName(), socketBindingName,
                            Element.SOCKET_BINDING_GROUP.getLocalName(), socketBindingGroupName);
                }
                validatedGroupList.add(socketBindingName);
                if (recursive && resource.getModel().hasDefined(ModelDescriptionConstants.INCLUDES)) {
                    List<ModelNode> includedSocketBindingGroups = resource.getModel().get(ModelDescriptionConstants.INCLUDES).asList();
                    for(ModelNode includedSocketBindingGroup : includedSocketBindingGroups){
                        String includedSocketBindingGroupName = includedSocketBindingGroup.asString();
                        if (!validatedGroupList.contains(includedSocketBindingGroupName)) {
                            Resource includedResource = context.readResourceFromRoot(PathAddress.pathAddress(ModelDescriptionConstants.SOCKET_BINDING_GROUP, includedSocketBindingGroupName), false);
                            validation(includedSocketBindingGroupName, socketBindingName, includedResource, recursive, validatedGroupList);
                        }
                    }
                }
            }
        }, Stage.MODEL);

        super.populateModel(context, operation, resource);
    }
}
