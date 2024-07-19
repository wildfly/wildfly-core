/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.services.net;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ServiceRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

/**
 * Resource definition for a remote-destination outbound socket binding
 *
 * @author Jaikiran Pai
 */
public class RemoteDestinationOutboundSocketBindingResourceDefinition extends OutboundSocketBindingResourceDefinition {

    public static final PathElement PATH = PathElement.pathElement(ModelDescriptionConstants.REMOTE_DESTINATION_OUTBOUND_SOCKET_BINDING);

    public static final SimpleAttributeDefinition HOST = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.HOST, ModelType.STRING, false)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, false, true))
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .build();

    public static final SimpleAttributeDefinition PORT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PORT, ModelType.INT, false)
            .setAllowExpression(true)
            .setValidator(new IntRangeValidator(0, 65535, false, true))
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .build();

    public static final SimpleAttributeDefinition[] ATTRIBUTES = {HOST, PORT, SOURCE_PORT, SOURCE_INTERFACE, FIXED_SOURCE_PORT};

    public static final RemoteDestinationOutboundSocketBindingResourceDefinition INSTANCE = new RemoteDestinationOutboundSocketBindingResourceDefinition();

    private RemoteDestinationOutboundSocketBindingResourceDefinition() {
        super(new Parameters(PATH, ControllerResolver.getResolver(ModelDescriptionConstants.REMOTE_DESTINATION_OUTBOUND_SOCKET_BINDING))
                .setAddHandler(RemoteDestinationOutboundSocketBindingAddHandler.INSTANCE)
                .setRemoveHandler(new ServiceRemoveStepHandler(RemoteDestinationOutboundSocketBindingAddHandler.INSTANCE))
        );
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (SimpleAttributeDefinition ad : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(ad, null, new OutboundSocketBindingWriteHandler(true));
        }
    }
}
