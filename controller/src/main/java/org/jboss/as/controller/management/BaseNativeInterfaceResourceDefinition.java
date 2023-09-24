/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.management;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NATIVE_INTERFACE;
import static org.jboss.as.controller.management.Capabilities.NATIVE_MANAGEMENT_CAPABILITY;
import static org.jboss.as.controller.management.Capabilities.SASL_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.jboss.as.controller.management.Capabilities.SSL_CONTEXT_CAPABILITY;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;


/**
 * Base class for the {@link ResourceDefinition} instances to extend from.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public abstract class BaseNativeInterfaceResourceDefinition extends SimpleResourceDefinition {

    public static final RuntimeCapability<Void> NATIVE_MANAGEMENT_RUNTIME_CAPABILITY = RuntimeCapability.Builder
            .of(NATIVE_MANAGEMENT_CAPABILITY).build();

    protected static final PathElement RESOURCE_PATH = PathElement.pathElement(MANAGEMENT_INTERFACE, NATIVE_INTERFACE);

    public static final SimpleAttributeDefinition SERVER_NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SERVER_NAME, ModelType.STRING, true)
        .setRequires(ModelDescriptionConstants.SECURITY_REALM)
        .setAllowExpression(true)
        .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
        .setRestartAllServices()
        .setDeprecated(ModelVersion.create(5))
        .build();

    public static final SimpleAttributeDefinition SASL_PROTOCOL = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SASL_PROTOCOL, ModelType.STRING, true)
        .setRequires(ModelDescriptionConstants.SECURITY_REALM)
        .setAllowExpression(true)
        .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
        .setDefaultValue(new ModelNode(ModelDescriptionConstants.REMOTE))
        .setRestartAllServices()
        .setDeprecated(ModelVersion.create(5))
        .build();

    public static final SimpleAttributeDefinition SASL_AUTHENTICATION_FACTORY = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SASL_AUTHENTICATION_FACTORY, ModelType.STRING, true)
        .setMinSize(1)
        .setRestartAllServices()
        .setCapabilityReference(SASL_AUTHENTICATION_FACTORY_CAPABILITY, NATIVE_MANAGEMENT_RUNTIME_CAPABILITY)
        .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.AUTHENTICATION_FACTORY_REF)
        .build();

    public static final SimpleAttributeDefinition SSL_CONTEXT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SSL_CONTEXT, ModelType.STRING, true)
        .setMinSize(1)
        .setRestartAllServices()
        .setCapabilityReference(SSL_CONTEXT_CAPABILITY, NATIVE_MANAGEMENT_RUNTIME_CAPABILITY)
        .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.SSL_REF)
        .build();

    protected static final AttributeDefinition[] COMMON_ATTRIBUTES = new AttributeDefinition[] { SSL_CONTEXT, SERVER_NAME, SASL_PROTOCOL, SASL_AUTHENTICATION_FACTORY };

    protected BaseNativeInterfaceResourceDefinition(Parameters parameters) {
        super(parameters
                .addAccessConstraints(SensitiveTargetAccessConstraintDefinition.MANAGEMENT_INTERFACES)
                .addCapabilities(NATIVE_MANAGEMENT_RUNTIME_CAPABILITY)
                .setDeprecatedSince(ModelVersion.create(1, 7))
        );
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        AttributeDefinition[] attributeDefinitions = getAttributeDefinitions();
        OperationStepHandler writeHandler = new ManagementWriteAttributeHandler(attributeDefinitions);
        for (AttributeDefinition attr : attributeDefinitions) {
            resourceRegistration.registerReadWriteAttribute(attr, null, writeHandler);
        }
    }

    protected abstract AttributeDefinition[] getAttributeDefinitions();

    protected static AttributeDefinition[] combine(AttributeDefinition[] commonAttributes, AttributeDefinition... additionalAttributes) {
        AttributeDefinition[] combined = new AttributeDefinition[commonAttributes.length + additionalAttributes.length];
        System.arraycopy(commonAttributes, 0, combined, 0, commonAttributes.length);
        System.arraycopy(additionalAttributes, 0, combined, commonAttributes.length, additionalAttributes.length);

        return combined;
    }
}
