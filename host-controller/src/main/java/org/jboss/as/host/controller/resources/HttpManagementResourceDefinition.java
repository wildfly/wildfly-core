/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.host.controller.resources;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HTTP_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_INTERFACE;

import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.host.controller.HostControllerEnvironment;
import org.jboss.as.host.controller.HostModelUtil;
import org.jboss.as.host.controller.operations.HttpManagementAddHandler;
import org.jboss.as.host.controller.operations.HttpManagementRemoveHandler;
import org.jboss.as.host.controller.operations.HttpManagementWriteAttributeHandler;
import org.jboss.as.host.controller.operations.LocalHostControllerInfoImpl;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for the HTTP management interface resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class HttpManagementResourceDefinition extends SimpleResourceDefinition {

    private static final String RUNTIME_CAPABILITY_NAME = "org.wildfly.management.http-interface";

    public static final RuntimeCapability<Void> HTTP_MANAGEMENT_CAPABILITY = RuntimeCapability.Builder
            .of(RUNTIME_CAPABILITY_NAME).build();

    private static final PathElement RESOURCE_PATH = PathElement.pathElement(MANAGEMENT_INTERFACE, HTTP_INTERFACE);

    public static final SimpleAttributeDefinition SECURITY_REALM = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SECURITY_REALM, ModelType.STRING, true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, false))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SECURITY_REALM_REF)
            .setNullSignificant(true)
            .build();

    public static final SimpleAttributeDefinition INTERFACE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.INTERFACE, ModelType.STRING, false)
            // expressions only allowed due to compatibility. allowing this was a mistake
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, false, true))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_CONFIG)
            .build();

    public static final SimpleAttributeDefinition HTTP_PORT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.PORT, ModelType.INT, true)
            .setAllowExpression(true).setValidator(new IntRangeValidator(0, 65535, true, true))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_CONFIG)
            .build();

    public static final SimpleAttributeDefinition HTTPS_PORT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SECURE_PORT, ModelType.INT, true)
            .setAllowExpression(true).setValidator(new IntRangeValidator(0, 65535, true, true))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_CONFIG)
            .setRequires(SECURITY_REALM.getName())
            .build();

    public static final SimpleAttributeDefinition SECURE_INTERFACE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SECURE_INTERFACE, ModelType.STRING, true)
            // SECURE_INTERFACE does not allow expressions. INTERFACE only does due to compatibility; otherwise it shouldn't
            .setAllowExpression(false)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, false))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_CONFIG)
            .setRequires(SECURITY_REALM.getName())
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
                .setAllowExpression(true)
                .setDefaultValue(new ModelNode(false))
                .build();

    public static final ObjectTypeAttributeDefinition HTTP_UPGRADE = new ObjectTypeAttributeDefinition.Builder(ModelDescriptionConstants.HTTP_UPGRADE, ENABLED)
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

    public static final AttributeDefinition[] ATTRIBUTE_DEFINITIONS = new AttributeDefinition[] {INTERFACE, HTTP_PORT, HTTPS_PORT, SECURE_INTERFACE, SECURITY_REALM,
                                                                                                 CONSOLE_ENABLED, HTTP_UPGRADE_ENABLED, HTTP_UPGRADE, SASL_PROTOCOL, SERVER_NAME, ALLOWED_ORIGINS};

    private final List<AccessConstraintDefinition> accessConstraints;

    public HttpManagementResourceDefinition(final LocalHostControllerInfoImpl hostControllerInfo,
                                             final HostControllerEnvironment environment) {
        super(RESOURCE_PATH,
                HostModelUtil.getResourceDescriptionResolver("core", "management", "http-interface"),
                new HttpManagementAddHandler(hostControllerInfo, environment),
                new HttpManagementRemoveHandler(hostControllerInfo, environment),
                OperationEntry.Flag.RESTART_NONE, OperationEntry.Flag.RESTART_NONE);
        this.accessConstraints = SensitiveTargetAccessConstraintDefinition.MANAGEMENT_INTERFACES.wrapAsList();
        setDeprecated(ModelVersion.create(1, 7));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition attr : ATTRIBUTE_DEFINITIONS) {
            if (attr.equals(HTTP_UPGRADE_ENABLED)) {
                HttpUpgradeAttributeHandler handler = new HttpUpgradeAttributeHandler();
                resourceRegistration.registerReadWriteAttribute(attr, handler, handler);
            } else {
                resourceRegistration.registerReadWriteAttribute(attr, null, HttpManagementWriteAttributeHandler.INSTANCE);
            }
        }
    }

    @Override
    public List<AccessConstraintDefinition> getAccessConstraints() {
        return accessConstraints;
    }

    private class HttpUpgradeAttributeHandler implements OperationStepHandler {

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
}
