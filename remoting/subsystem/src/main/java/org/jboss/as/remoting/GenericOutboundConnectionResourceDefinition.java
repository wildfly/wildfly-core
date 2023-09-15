/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import java.util.Collection;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.ServiceRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

/**
 * @author Jaikiran Pai
 */
class GenericOutboundConnectionResourceDefinition extends AbstractOutboundConnectionResourceDefinition {

    static final PathElement PATH = PathElement.pathElement(CommonAttributes.OUTBOUND_CONNECTION);

    static final SimpleAttributeDefinition URI = new SimpleAttributeDefinitionBuilder(CommonAttributes.URI, ModelType.STRING, false)
            .setAllowExpression(true).setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, false, true))
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES).build();

    static final Collection<AttributeDefinition> ATTRIBUTES = List.of(URI);

    GenericOutboundConnectionResourceDefinition() {
        this(new GenericOutboundConnectionAdd());
    }

    private GenericOutboundConnectionResourceDefinition(GenericOutboundConnectionAdd addHandler) {
        super(new Parameters(PATH, RemotingExtension.getResourceDescriptionResolver(CommonAttributes.OUTBOUND_CONNECTION))
                .setAddHandler(addHandler)
                .setRemoveHandler(new ServiceRemoveStepHandler(OUTBOUND_CONNECTION_CAPABILITY.getCapabilityServiceName(), addHandler))
                .setDeprecatedSince(RemotingSubsystemModel.VERSION_4_0_0.getVersion())
        );
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        // TODO why does this resource have properties that are never referenced?
        resourceRegistration.registerSubModel(new PropertyResource(CommonAttributes.OUTBOUND_CONNECTION));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition attribute : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attribute, null, ReloadRequiredWriteAttributeHandler.INSTANCE);
        }
    }
}
