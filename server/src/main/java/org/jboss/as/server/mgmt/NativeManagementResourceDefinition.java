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

package org.jboss.as.server.mgmt;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NATIVE_INTERFACE;

import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.as.server.operations.NativeManagementAddHandler;
import org.jboss.as.server.operations.NativeManagementRemoveHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for the native management interface resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class NativeManagementResourceDefinition extends SimpleResourceDefinition {

    public static final String SOCKET_BINDING_CAPABILITY_NAME = "org.wildfly.network.socket-binding";
    private static final String RUNTIME_CAPABILITY_NAME = "org.wildfly.management.native-interface";
    public static final RuntimeCapability<Void> NATIVE_MANAGEMENT_CAPABILITY = RuntimeCapability.Builder
            .of(RUNTIME_CAPABILITY_NAME).build();

    private static final PathElement RESOURCE_PATH = PathElement.pathElement(MANAGEMENT_INTERFACE, NATIVE_INTERFACE);

    public static final SimpleAttributeDefinition SECURITY_REALM = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SECURITY_REALM, ModelType.STRING, true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, false))
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .addAccessConstraint(new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.SECURITY_REALM_REF))
            .setNullSignificant(true)
            .build();

    public static final SimpleAttributeDefinition SOCKET_BINDING = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SOCKET_BINDING, ModelType.STRING, false)
            .setXmlName(Attribute.NATIVE.getLocalName())
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, false))
            .setAlternatives(ModelDescriptionConstants.INTERFACE)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .addAccessConstraint(new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.SOCKET_CONFIG))
            .setCapabilityReference(SOCKET_BINDING_CAPABILITY_NAME, RUNTIME_CAPABILITY_NAME, false)
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

    public static final AttributeDefinition[] ATTRIBUTE_DEFINITIONS = new AttributeDefinition[] {SECURITY_REALM, SOCKET_BINDING, SERVER_NAME, SASL_PROTOCOL };

    public static final NativeManagementResourceDefinition INSTANCE = new NativeManagementResourceDefinition();

    private final List<AccessConstraintDefinition> accessConstraints;

    private NativeManagementResourceDefinition() {
        super(RESOURCE_PATH,
                ServerDescriptions.getResourceDescriptionResolver("core.management.native-interface"),
                NativeManagementAddHandler.INSTANCE, NativeManagementRemoveHandler.INSTANCE,
                OperationEntry.Flag.RESTART_NONE, OperationEntry.Flag.RESTART_NONE);
        this.accessConstraints = SensitiveTargetAccessConstraintDefinition.MANAGEMENT_INTERFACES.wrapAsList();
        setDeprecated(ModelVersion.create(3));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        OperationStepHandler writeHandler = new ReloadRequiredWriteAttributeHandler(ATTRIBUTE_DEFINITIONS);
        for (AttributeDefinition attr : ATTRIBUTE_DEFINITIONS) {
            resourceRegistration.registerReadWriteAttribute(attr, null, writeHandler);
        }
    }

    @Override
    public List<AccessConstraintDefinition> getAccessConstraints() {
        return accessConstraints;
    }

    @Override
    public void registerCapabilities(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerCapability(NATIVE_MANAGEMENT_CAPABILITY);
    }
}
