/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import static org.jboss.as.remoting.Capabilities.AUTHENTICATION_CONTEXT_CAPABILITY;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ServiceRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Jaikiran Pai
 */
class RemoteOutboundConnectionResourceDefinition extends AbstractOutboundConnectionResourceDefinition {

    static final PathElement ADDRESS = PathElement.pathElement(CommonAttributes.REMOTE_OUTBOUND_CONNECTION);

    public static final SimpleAttributeDefinition OUTBOUND_SOCKET_BINDING_REF = new SimpleAttributeDefinitionBuilder(CommonAttributes.OUTBOUND_SOCKET_BINDING_REF, ModelType.STRING, false)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, false, true))
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_BINDING_REF)
            .setCapabilityReference(OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME, OUTBOUND_CONNECTION_CAPABILITY)
            .build();

    public static final SimpleAttributeDefinition USERNAME = new SimpleAttributeDefinitionBuilder(CommonAttributes.USERNAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setAlternatives(CommonAttributes.AUTHENTICATION_CONTEXT)
            .setDeprecated(ModelVersion.create(4))
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.CREDENTIAL)
            .addAccessConstraint(RemotingExtension.REMOTING_SECURITY_DEF)
            .build();

    public static final SimpleAttributeDefinition SECURITY_REALM = new SimpleAttributeDefinitionBuilder(CommonAttributes.SECURITY_REALM, ModelType.STRING, true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, false))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SECURITY_REALM_REF)
            .addAccessConstraint(RemotingExtension.REMOTING_SECURITY_DEF)
            .setAlternatives(CommonAttributes.AUTHENTICATION_CONTEXT)
            .setDeprecated(ModelVersion.create(4))
            .build();

    public static final SimpleAttributeDefinition PROTOCOL = new SimpleAttributeDefinitionBuilder(
            CommonAttributes.PROTOCOL, ModelType.STRING, true).setValidator(EnumValidator.create(Protocol.class))
            .setDefaultValue(new ModelNode(Protocol.HTTP_REMOTING.toString()))
            .setAllowExpression(true)
            .setAlternatives(CommonAttributes.AUTHENTICATION_CONTEXT)
            .setDeprecated(ModelVersion.create(4))
            .build();

    public static final SimpleAttributeDefinition AUTHENTICATION_CONTEXT = new SimpleAttributeDefinitionBuilder(CommonAttributes.AUTHENTICATION_CONTEXT, ModelType.STRING, true)
            .setCapabilityReference(AUTHENTICATION_CONTEXT_CAPABILITY, OUTBOUND_CONNECTION_CAPABILITY_NAME)
            .setAlternatives(CommonAttributes.USERNAME, CommonAttributes.SECURITY_REALM, CommonAttributes.PROTOCOL)
            .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.AUTHENTICATION_CLIENT_REF)
            .build();

    public static final AttributeDefinition[] ATTRIBUTE_DEFINITIONS = {
        OUTBOUND_SOCKET_BINDING_REF, USERNAME, SECURITY_REALM, PROTOCOL, AUTHENTICATION_CONTEXT
    };

    static final RemoteOutboundConnectionResourceDefinition INSTANCE = new RemoteOutboundConnectionResourceDefinition();

    private RemoteOutboundConnectionResourceDefinition() {
        super(new Parameters(ADDRESS, RemotingExtension.getResourceDescriptionResolver(CommonAttributes.REMOTE_OUTBOUND_CONNECTION))
                .setAddHandler(RemoteOutboundConnectionAdd.INSTANCE)
                .setRemoveHandler(new ServiceRemoveStepHandler(OUTBOUND_CONNECTION_CAPABILITY.getCapabilityServiceName(), RemoteOutboundConnectionAdd.INSTANCE))
        );
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(new PropertyResource(CommonAttributes.REMOTE_OUTBOUND_CONNECTION));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        resourceRegistration.registerReadWriteAttribute(OUTBOUND_SOCKET_BINDING_REF, null, RemoteOutboundConnectionWriteHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(USERNAME, null, RemoteOutboundConnectionWriteHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(SECURITY_REALM, null, RemoteOutboundConnectionWriteHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(PROTOCOL, null, RemoteOutboundConnectionWriteHandler.INSTANCE);
        resourceRegistration.registerReadWriteAttribute(AUTHENTICATION_CONTEXT, null, RemoteOutboundConnectionWriteHandler.INSTANCE);
    }

    @Override
    protected OperationStepHandler getWriteAttributeHandler(AttributeDefinition attribute) {
        // we ignore the passed attribute, since all attribute writes lead to the
        // same action - i.e. restart the service
        return RemoteOutboundConnectionWriteHandler.INSTANCE;
    }
}
