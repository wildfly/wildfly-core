/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import static org.jboss.as.remoting.Capabilities.SASL_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.jboss.as.remoting.CommonAttributes.HTTP_CONNECTOR;
import static org.jboss.as.remoting.ConnectorCommon.SASL_PROTOCOL;
import static org.jboss.as.remoting.ConnectorCommon.SERVER_NAME;
import static org.jboss.as.remoting.ConnectorResource.CONNECTOR_CAPABILITY;

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
import org.jboss.as.controller.registry.RuntimePackageDependency;
import org.jboss.dmr.ModelType;

/**
 * @author Stuart Douglas
 */
public class HttpConnectorResource extends SimpleResourceDefinition {

    private static final String HTTP_CONNECTOR_CAPABILITY_NAME = "org.wildfly.remoting.http-connector";
    static final RuntimeCapability<Void> HTTP_CONNECTOR_CAPABILITY =
            RuntimeCapability.Builder.of(HTTP_CONNECTOR_CAPABILITY_NAME, true)
                    .build();

    static final PathElement PATH = PathElement.pathElement(CommonAttributes.HTTP_CONNECTOR);

    //FIXME is this attribute still used?
    static final SimpleAttributeDefinition AUTHENTICATION_PROVIDER = new SimpleAttributeDefinitionBuilder(CommonAttributes.AUTHENTICATION_PROVIDER, ModelType.STRING)
            .setDefaultValue(null)
            .setRequired(false)
            .addAccessConstraint(RemotingExtension.REMOTING_SECURITY_DEF)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition CONNECTOR_REF = new SimpleAttributeDefinitionBuilder(CommonAttributes.CONNECTOR_REF, ModelType.STRING)
            .setRequired(true)
            .setAllowExpression(false)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition SECURITY_REALM = new SimpleAttributeDefinitionBuilder(CommonAttributes.SECURITY_REALM, ModelType.STRING, true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, false))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SECURITY_REALM_REF)
            .addAccessConstraint(RemotingExtension.REMOTING_SECURITY_DEF)
            .setRestartAllServices()
            .setDeprecated(RemotingSubsystemModel.VERSION_6_0_0.getVersion())
            .build();

    static final SimpleAttributeDefinition SASL_AUTHENTICATION_FACTORY = new SimpleAttributeDefinitionBuilder(ConnectorCommon.SASL_AUTHENTICATION_FACTORY)
            .setCapabilityReference(SASL_AUTHENTICATION_FACTORY_CAPABILITY, HTTP_CONNECTOR_CAPABILITY)
            .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.AUTHENTICATION_FACTORY_REF)
            .setRestartAllServices()
            .build();

    static final Collection<AttributeDefinition> ATTRIBUTES = List.of(AUTHENTICATION_PROVIDER, CONNECTOR_REF, SECURITY_REALM, SERVER_NAME, SASL_AUTHENTICATION_FACTORY, SASL_PROTOCOL);

    HttpConnectorResource() {
        super(new Parameters(PATH, RemotingExtension.getResourceDescriptionResolver(HTTP_CONNECTOR))
                .setAddHandler(HttpConnectorAdd.INSTANCE)
                .setRemoveHandler(HttpConnectorRemove.INSTANCE)
                // expose a common connector capability (WFCORE-4875)
                .setCapabilities(CONNECTOR_CAPABILITY, HTTP_CONNECTOR_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        final OperationStepHandler writeHandler = new ReloadRequiredWriteAttributeHandler(ATTRIBUTES);
        for (AttributeDefinition attribute : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attribute, null, writeHandler);
        }
    }

    @Override
    public void registerAdditionalRuntimePackages(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerAdditionalRuntimePackages(RuntimePackageDependency.required("io.undertow.core"));
    }
}
