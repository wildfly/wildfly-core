/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.services.net;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ServiceRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

/**
 * Resource definition for a local-destination outbound socket binding
 *
 * @author Jaikiran Pai
 */
public class LocalDestinationOutboundSocketBindingResourceDefinition extends OutboundSocketBindingResourceDefinition {

    static final String SOCKET_BINDING_CAPABILITY_NAME = "org.wildfly.network.socket-binding";

    public static final SimpleAttributeDefinition SOCKET_BINDING_REF = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SOCKET_BINDING_REF, ModelType.STRING, false)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, false, true))
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_BINDING_REF)
            .setCapabilityReference(SOCKET_BINDING_CAPABILITY_NAME, OUTBOUND_SOCKET_BINDING_CAPABILITY)
            .build();

    public static final SimpleAttributeDefinition[] ATTRIBUTES = {SOURCE_PORT, SOURCE_INTERFACE, FIXED_SOURCE_PORT, SOCKET_BINDING_REF};

    public static final LocalDestinationOutboundSocketBindingResourceDefinition INSTANCE = new LocalDestinationOutboundSocketBindingResourceDefinition();

    private LocalDestinationOutboundSocketBindingResourceDefinition() {
        super(new Parameters(PathElement.pathElement(ModelDescriptionConstants.LOCAL_DESTINATION_OUTBOUND_SOCKET_BINDING),
                ControllerResolver.getResolver(ModelDescriptionConstants.LOCAL_DESTINATION_OUTBOUND_SOCKET_BINDING))
                .setAddHandler(LocalDestinationOutboundSocketBindingAddHandler.INSTANCE)
                .setRemoveHandler(new ServiceRemoveStepHandler(LocalDestinationOutboundSocketBindingAddHandler.INSTANCE))
        );
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (SimpleAttributeDefinition ad : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(ad, null, new OutboundSocketBindingWriteHandler(ad, false));
        }
    }
}
