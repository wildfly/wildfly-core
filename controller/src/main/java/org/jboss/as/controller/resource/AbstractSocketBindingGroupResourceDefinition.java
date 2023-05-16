/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
                .setCapabilityReference(InterfaceDefinition.INTERFACE_CAPABILITY_NAME, dependentCapability)
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
