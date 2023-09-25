/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ServiceRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

/**
 * @author Jaikiran Pai
 */
class LocalOutboundConnectionResourceDefinition extends AbstractOutboundConnectionResourceDefinition {

    static final PathElement ADDRESS = PathElement.pathElement(CommonAttributes.LOCAL_OUTBOUND_CONNECTION);

    public static final SimpleAttributeDefinition OUTBOUND_SOCKET_BINDING_REF = new SimpleAttributeDefinitionBuilder(CommonAttributes.OUTBOUND_SOCKET_BINDING_REF, ModelType.STRING, false)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, false, true))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_BINDING_REF)
            .setCapabilityReference(OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME, OUTBOUND_CONNECTION_CAPABILITY)
            .build();

    static final LocalOutboundConnectionResourceDefinition INSTANCE = new LocalOutboundConnectionResourceDefinition();

    private LocalOutboundConnectionResourceDefinition() {
        super(new Parameters(ADDRESS, RemotingExtension.getResourceDescriptionResolver(CommonAttributes.LOCAL_OUTBOUND_CONNECTION))
                .setAddHandler(LocalOutboundConnectionAdd.INSTANCE)
                .setRemoveHandler(new ServiceRemoveStepHandler(OUTBOUND_CONNECTION_CAPABILITY.getCapabilityServiceName(), LocalOutboundConnectionAdd.INSTANCE))
                .setDeprecatedSince(ModelVersion.create(4))
        );
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(new PropertyResource(CommonAttributes.LOCAL_OUTBOUND_CONNECTION));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadWriteAttribute(OUTBOUND_SOCKET_BINDING_REF, null, LocalOutboundConnectionWriteHandler.INSTANCE);
    }

    @Override
    protected OperationStepHandler getWriteAttributeHandler(final AttributeDefinition attribute) {
        // we ignore the passed attribute, since all attribute writes lead to the
        // same action - i.e. restart the service
        return LocalOutboundConnectionWriteHandler.INSTANCE;
    }
}
