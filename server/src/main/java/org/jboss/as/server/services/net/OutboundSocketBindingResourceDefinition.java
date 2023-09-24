/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.services.net;

import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.network.OutboundSocketBinding;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Represents a {@link org.jboss.as.controller.ResourceDefinition} for an outbound-socket-binding
 *
 * @author Jaikiran Pai
 */
public abstract class OutboundSocketBindingResourceDefinition extends SimpleResourceDefinition {

    private static final String OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME = "org.wildfly.network.outbound-socket-binding";
    static final RuntimeCapability<Void> OUTBOUND_SOCKET_BINDING_CAPABILITY =
            RuntimeCapability.Builder.of(OutboundSocketBindingResourceDefinition.OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME,
                    true, OutboundSocketBinding.class)
                    .build();

    /*public static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.NAME, ModelType.STRING, false)
            .setValidator(new StringLengthValidator(1)).build();*/

    public static final SimpleAttributeDefinition SOURCE_PORT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SOURCE_PORT, ModelType.INT, true)
            .setAllowExpression(true).setValidator(new IntRangeValidator(0, 65535, true, true))
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES).build();

    public static final SimpleAttributeDefinition SOURCE_INTERFACE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.SOURCE_INTERFACE, ModelType.STRING, true)
            .setAllowExpression(true)
            .setExpressionsDeprecated()
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .setCapabilityReference("org.wildfly.network.interface", OUTBOUND_SOCKET_BINDING_CAPABILITY)
            .build();

    public static final SimpleAttributeDefinition FIXED_SOURCE_PORT = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.FIXED_SOURCE_PORT, ModelType.BOOLEAN, true)
            .setAllowExpression(true).setDefaultValue(ModelNode.FALSE)
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .build();

    protected OutboundSocketBindingResourceDefinition(final Parameters parameters) {
        super(parameters.addCapabilities(OUTBOUND_SOCKET_BINDING_CAPABILITY));
    }
}
