/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.server.services.net;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.EnumSet;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.interfaces.ParsedInterfaceCriteria;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.resource.InterfaceDefinition;
import org.jboss.as.network.NetworkInterfaceBinding;
import org.jboss.as.network.NetworkUtils;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Handler that resolves an interface criteria to actual IP addresses in order to allow clients to check the validity
 * of the configuration.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class SpecifiedInterfaceResolveHandler implements OperationStepHandler {

    static final AttributeDefinition[] ATTRIBUTES = InterfaceDefinition.ROOT_ATTRIBUTES;
    private static final String OPERATION_NAME = "resolve-internet-address";

    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME,
            ServerDescriptions.getResourceDescriptionResolver(INTERFACE))
            .setParameters(ATTRIBUTES)
            .setReplyType(ModelType.STRING)
            .setAttributeResolver(ControllerResolver.getResolver(INTERFACE))
            .withFlags(EnumSet.of(OperationEntry.Flag.RUNTIME_ONLY))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_CONFIG)
            .build();

    public static final SpecifiedInterfaceResolveHandler INSTANCE = new SpecifiedInterfaceResolveHandler();

    private SpecifiedInterfaceResolveHandler() {
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {

        final ModelNode config = new ModelNode();

        for(final AttributeDefinition definition : ATTRIBUTES) {
            validateAndSet(definition, operation, config);
        }

        ParsedInterfaceCriteria parsed = ParsedInterfaceCriteria.parse(config, true, context);
        if (parsed.getFailureMessage() != null) {
            throw new OperationFailedException(parsed.getFailureMessage());
        }

        try {
            NetworkInterfaceBinding nib = NetworkInterfaceService.createBinding(parsed);
            context.getResult().set(NetworkUtils.canonize(nib.getAddress().getHostAddress()));
        } catch (SocketException e) {
            throw ServerLogger.ROOT_LOGGER.cannotResolveInterface(e, e);
        } catch (UnknownHostException e) {
            throw ServerLogger.ROOT_LOGGER.cannotResolveInterface(e, e);
        }
    }

    private void validateAndSet(final AttributeDefinition definition, final ModelNode operation, final ModelNode subModel) throws OperationFailedException {
        final String attributeName = definition.getName();
        final boolean has = operation.has(attributeName);
        if(! has && definition.isRequired(operation)) {
            throw ControllerLogger.ROOT_LOGGER.required(attributeName);
        }
        if(has) {
            if(! definition.isAllowed(operation)) {
                throw new OperationFailedException(ControllerLogger.ROOT_LOGGER.invalid(attributeName));
            }
            definition.validateAndSet(operation, subModel);
        } else {
            // create the undefined node
            subModel.get(definition.getName());
        }
    }

}
