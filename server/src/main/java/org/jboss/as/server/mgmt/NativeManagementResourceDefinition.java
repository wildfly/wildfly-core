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

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.management.BaseNativeInterfaceResourceDefinition;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.server.controller.descriptions.ServerDescriptions;
import org.jboss.as.server.operations.NativeManagementAddHandler;
import org.jboss.as.server.operations.NativeManagementRemoveHandler;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for the native management interface resource.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class NativeManagementResourceDefinition extends BaseNativeInterfaceResourceDefinition {

    public static final String SOCKET_BINDING_CAPABILITY_NAME = "org.wildfly.network.socket-binding";

    private static final PathElement RESOURCE_PATH = PathElement.pathElement(MANAGEMENT_INTERFACE, NATIVE_INTERFACE);

    public static final SimpleAttributeDefinition SOCKET_BINDING = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SOCKET_BINDING, ModelType.STRING, false)
            .setXmlName(Attribute.NATIVE.getLocalName())
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, false))
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .addAccessConstraint(new SensitiveTargetAccessConstraintDefinition(SensitivityClassification.SOCKET_CONFIG))
            .setCapabilityReference(SOCKET_BINDING_CAPABILITY_NAME, RUNTIME_CAPABILITY_NAME, false)
            .build();

    public static final AttributeDefinition[] ATTRIBUTE_DEFINITIONS = combine(COMMON_ATTRIBUTES, SOCKET_BINDING);

    public static final NativeManagementResourceDefinition INSTANCE = new NativeManagementResourceDefinition();

    private NativeManagementResourceDefinition() {
        super(new Parameters(RESOURCE_PATH, ServerDescriptions.getResourceDescriptionResolver("core.management.native-interface"))
            .setAddHandler(NativeManagementAddHandler.INSTANCE)
            .setRemoveHandler(NativeManagementRemoveHandler.INSTANCE)
            .setAddRestartLevel(OperationEntry.Flag.RESTART_NONE)
            .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES));
    }

    @Override
    protected AttributeDefinition[] getAttributeDefinitions() {
        return ATTRIBUTE_DEFINITIONS;
    }

}
