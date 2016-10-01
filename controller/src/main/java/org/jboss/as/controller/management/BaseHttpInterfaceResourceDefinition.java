/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller.management;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HTTP_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_INTERFACE;
import static org.jboss.as.controller.management.Capabilities.HTTP_MANAGEMENT_CAPABILITY;
import static org.jboss.as.controller.management.Capabilities.HTTP_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.jboss.as.controller.management.Capabilities.SASL_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.jboss.as.controller.management.Capabilities.SSL_CONTEXT_CAPABILITY;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Base class for the {@link ResourceDefinition} instances to extend from.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public abstract class BaseHttpInterfaceResourceDefinition extends SimpleResourceDefinition {

    public static final RuntimeCapability<Void> HTTP_MANAGEMENT_RUNTIME_CAPABILITY = RuntimeCapability.Builder.of(HTTP_MANAGEMENT_CAPABILITY)
        .build();

    protected static final PathElement RESOURCE_PATH = PathElement.pathElement(MANAGEMENT_INTERFACE, HTTP_INTERFACE);

    public static final SimpleAttributeDefinition HTTP_AUTHENTICATION_FACTORY = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.HTTP_AUTHENTICATION_FACTORY, ModelType.STRING, true)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(HTTP_AUTHENTICATION_FACTORY_CAPABILITY, HTTP_MANAGEMENT_RUNTIME_CAPABILITY)
        .build();

    public static final SimpleAttributeDefinition SECURITY_REALM = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SECURITY_REALM, ModelType.STRING, true)
        .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, false))
        .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SECURITY_REALM_REF)
        .setNullSignificant(true)
        .build();

    public static final SimpleAttributeDefinition SSL_CONTEXT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SSL_CONTEXT, ModelType.STRING, true)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(SSL_CONTEXT_CAPABILITY, HTTP_MANAGEMENT_RUNTIME_CAPABILITY)
        .build();

    public static final SimpleAttributeDefinition CONSOLE_ENABLED = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.CONSOLE_ENABLED, ModelType.BOOLEAN, true)
        .setAllowExpression(true)
        .setXmlName(Attribute.CONSOLE_ENABLED.getLocalName())
        .setDefaultValue(new ModelNode(true))
        .build();

    public static final SimpleAttributeDefinition HTTP_UPGRADE_ENABLED = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.HTTP_UPGRADE_ENABLED, ModelType.BOOLEAN, true)
        .setXmlName(Attribute.HTTP_UPGRADE_ENABLED.getLocalName())
        .setDeprecated(ModelVersion.create(5), true)
        .build();

    public static final SimpleAttributeDefinition ENABLED = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.ENABLED, ModelType.BOOLEAN, true)
        .setDefaultValue(new ModelNode(false))
        .build();

    public static final SimpleAttributeDefinition SASL_AUTHENTICATION_FACTORY = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SASL_AUTHENTICATION_FACTORY, ModelType.STRING, true)
        .setMinSize(1)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .setCapabilityReference(SASL_AUTHENTICATION_FACTORY_CAPABILITY, HTTP_MANAGEMENT_RUNTIME_CAPABILITY)
        .build();

    public static final ObjectTypeAttributeDefinition HTTP_UPGRADE = new ObjectTypeAttributeDefinition.Builder(ModelDescriptionConstants.HTTP_UPGRADE, ENABLED, SASL_AUTHENTICATION_FACTORY)
        .build();

    public static final SimpleAttributeDefinition SERVER_NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SERVER_NAME, ModelType.STRING, true)
        .setAllowExpression(true)
        .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .build();

    public static final SimpleAttributeDefinition SASL_PROTOCOL = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SASL_PROTOCOL, ModelType.STRING, true)
        .setAllowExpression(true)
        .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
        .setDefaultValue(new ModelNode(ModelDescriptionConstants.REMOTE))
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .build();

    public static final StringListAttributeDefinition ALLOWED_ORIGINS = new StringListAttributeDefinition.Builder(ModelDescriptionConstants.ALLOWED_ORIGINS)
        .setAllowExpression(true)
        .setAllowNull(true)
        .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, false))
        .setAttributeMarshaller(AttributeMarshaller.STRING_LIST)
        .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
        .build();

    protected static final AttributeDefinition[] COMMON_ATTRIBUTES = new AttributeDefinition[] { HTTP_AUTHENTICATION_FACTORY, SSL_CONTEXT, SECURITY_REALM, CONSOLE_ENABLED, HTTP_UPGRADE_ENABLED,
                                                                                                     HTTP_UPGRADE, SASL_PROTOCOL, SERVER_NAME, ALLOWED_ORIGINS};

    /**
     * @param parameters
     */
    protected BaseHttpInterfaceResourceDefinition(Parameters parameters) {
        super(parameters
                .addAccessConstraints(SensitiveTargetAccessConstraintDefinition.MANAGEMENT_INTERFACES)
                .addCapabilities(HTTP_MANAGEMENT_RUNTIME_CAPABILITY)
                .setDeprecatedSince(ModelVersion.create(1, 7))
        );
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        AttributeDefinition[] attributeDefinitions = getAttributeDefinitions();
        OperationStepHandler defaultWriteHandler = new ManagementWriteAttributeHandler(attributeDefinitions);
        for (AttributeDefinition attr : attributeDefinitions) {
            if (attr.equals(HTTP_UPGRADE_ENABLED)) {
                HttpUpgradeAttributeHandler handler = new HttpUpgradeAttributeHandler();
                resourceRegistration.registerReadWriteAttribute(attr, handler, handler);
            } else {
                resourceRegistration.registerReadWriteAttribute(attr, null, defaultWriteHandler);
            }
        }
    }

    protected abstract AttributeDefinition[] getAttributeDefinitions();

    protected class HttpUpgradeAttributeHandler implements OperationStepHandler {

        public HttpUpgradeAttributeHandler() {
        }

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            final Resource resource = context.readResourceForUpdate(PathAddress.EMPTY_ADDRESS);
            final ModelNode submodel = resource.getModel();
            final ModelNode httpUpgrade = submodel.get(ModelDescriptionConstants.HTTP_UPGRADE);

            String operationName = operation.require(ModelDescriptionConstants.OP).asString();
            assert ModelDescriptionConstants.HTTP_UPGRADE_ENABLED.equals(operation.require(ModelDescriptionConstants.NAME).asString());
            switch (operationName) {
                case ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION:
                    context.getResult().set(ENABLED.resolveModelAttribute(context, httpUpgrade));
                    break;
                case ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION:
                    httpUpgrade.get(ModelDescriptionConstants.ENABLED).set(operation.require(ModelDescriptionConstants.VALUE).asBoolean());
                    context.reloadRequired();
                    break;
            }

        }

    }

    protected static AttributeDefinition[] combine(AttributeDefinition[] commonAttributes, AttributeDefinition... additionalAttributes) {
        AttributeDefinition[] combined = new AttributeDefinition[commonAttributes.length + additionalAttributes.length];
        System.arraycopy(commonAttributes, 0, combined, 0, commonAttributes.length);
        System.arraycopy(additionalAttributes, 0, combined, commonAttributes.length, additionalAttributes.length);

        return combined;
    }

}
