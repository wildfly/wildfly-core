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

package org.jboss.as.controller.resource;


import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for a resource representing a socket binding group.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public abstract class AbstractSocketBindingGroupResourceDefinition extends SimpleResourceDefinition {

    // Common attributes

    public static final PathElement PATH = PathElement.pathElement(ModelDescriptionConstants.SOCKET_BINDING_GROUP);

    public static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.NAME, ModelType.STRING, false)
            .setResourceOnly()
            .setValidator(new StringLengthValidator(1)).build();

    protected static SimpleAttributeDefinition createDefaultInterface(RuntimeCapability dependentCapability) {
        return new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.DEFAULT_INTERFACE, ModelType.STRING, false)
                .setAllowExpression(true)
                .setExpressionsDeprecated()
                .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, false, true))
                .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
                .setCapabilityReference("org.wildfly.network.interface", dependentCapability)
                .build();
    }

    private final AttributeDefinition defaultInterface;

    public AbstractSocketBindingGroupResourceDefinition(final OperationStepHandler addHandler,
                                                        final OperationStepHandler removeHandler,
                                                        final AttributeDefinition defaultInterface,
                                                        final RuntimeCapability exposedCapability) {
        super(new Parameters(PATH, ControllerResolver.getResolver(ModelDescriptionConstants.SOCKET_BINDING_GROUP))
                .setAddHandler(addHandler)
                .setRemoveHandler(removeHandler)
                .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.SOCKET_CONFIG)
                .setCapabilities(exposedCapability));
        this.defaultInterface = defaultInterface;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);

        resourceRegistration.registerReadOnlyAttribute(NAME, null);
        resourceRegistration.registerReadWriteAttribute(defaultInterface, null, new ReloadRequiredWriteAttributeHandler(defaultInterface));
    }
}
