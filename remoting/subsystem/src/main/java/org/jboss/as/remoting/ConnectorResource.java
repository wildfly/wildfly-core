/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import static org.jboss.as.remoting.Capabilities.SASL_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.jboss.as.remoting.Capabilities.SSL_CONTEXT_CAPABILITY;
import static org.jboss.as.remoting.CommonAttributes.CONNECTOR;
import static org.jboss.as.remoting.ConnectorCommon.SASL_PROTOCOL;
import static org.jboss.as.remoting.ConnectorCommon.SERVER_NAME;

import java.util.Collection;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.network.ProtocolSocketBinding;
import org.jboss.dmr.ModelType;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ConnectorResource extends SimpleResourceDefinition {

    static final PathElement PATH = PathElement.pathElement(CommonAttributes.CONNECTOR);

    static final String SOCKET_CAPABILITY_NAME = "org.wildfly.network.socket-binding";
    private static final String CONNECTOR_CAPABILITY_NAME = "org.wildfly.remoting.connector";
    static final RuntimeCapability<Void> CONNECTOR_CAPABILITY =
            RuntimeCapability.Builder.of(CONNECTOR_CAPABILITY_NAME, true, ProtocolSocketBinding.class)
                    .setAllowMultipleRegistrations(true)
                    .build();

    //FIXME is this attribute still used?
    static final SimpleAttributeDefinition AUTHENTICATION_PROVIDER = new SimpleAttributeDefinitionBuilder(CommonAttributes.AUTHENTICATION_PROVIDER, ModelType.STRING)
            .setDefaultValue(null)
            .setRequired(false)
            .addAccessConstraint(RemotingExtension.REMOTING_SECURITY_DEF)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition SOCKET_BINDING = new SimpleAttributeDefinitionBuilder(CommonAttributes.SOCKET_BINDING, ModelType.STRING, false)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_BINDING_REF)
            .setCapabilityReference(SOCKET_CAPABILITY_NAME, CONNECTOR_CAPABILITY)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition SECURITY_REALM = new SimpleAttributeDefinitionBuilder(CommonAttributes.SECURITY_REALM, ModelType.STRING, true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, false))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SECURITY_REALM_REF)
            .addAccessConstraint(RemotingExtension.REMOTING_SECURITY_DEF)
            .setNullSignificant(true)
            .setRestartAllServices()
            .setDeprecated(RemotingSubsystemModel.VERSION_6_0_0.getVersion())
            .build();

    static final SimpleAttributeDefinition SASL_AUTHENTICATION_FACTORY = new SimpleAttributeDefinitionBuilder(ConnectorCommon.SASL_AUTHENTICATION_FACTORY)
            .setCapabilityReference(SASL_AUTHENTICATION_FACTORY_CAPABILITY, CONNECTOR_CAPABILITY)
            .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.AUTHENTICATION_FACTORY_REF)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition SSL_CONTEXT = new SimpleAttributeDefinitionBuilder(CommonAttributes.SSL_CONTEXT, ModelType.STRING, true)
            .addAccessConstraint(RemotingExtension.REMOTING_SECURITY_DEF)
            .setNullSignificant(true)
            .setCapabilityReference(SSL_CONTEXT_CAPABILITY, CONNECTOR_CAPABILITY)
            .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.SSL_REF)
            .setRestartAllServices()
            .build();

    static final Collection<AttributeDefinition> ATTRIBUTES  = List.of(AUTHENTICATION_PROVIDER, SOCKET_BINDING, SECURITY_REALM,
            SERVER_NAME, SASL_PROTOCOL, SASL_AUTHENTICATION_FACTORY, SSL_CONTEXT);

    ConnectorResource() {
        super(new Parameters(PATH, RemotingExtension.getResourceDescriptionResolver(CONNECTOR))
                .setAddHandler(ConnectorAdd.INSTANCE)
                .setRemoveHandler(ConnectorRemove.INSTANCE)
                .setCapabilities(CONNECTOR_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        final OperationStepHandler writeHandler = new ReloadRequiredWriteAttributeHandler(ATTRIBUTES);
        for (AttributeDefinition ad : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(ad, null, writeHandler);
        }
    }

}
