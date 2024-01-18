/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.operations.validation.InetAddressValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.MaskedAddressValidator;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class OutboundBindAddressResourceDefinition extends PersistentResourceDefinition {
    static final SimpleAttributeDefinition MATCH = new SimpleAttributeDefinitionBuilder("match", ModelType.STRING)
        .setRequired(true)
        .setAllowExpression(true)
        .setValidator(new MaskedAddressValidator(false, true))
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition BIND_ADDRESS = new SimpleAttributeDefinitionBuilder("bind-address", ModelType.STRING)
        .setRequired(true)
        .setAllowExpression(true)
        .setValidator(new InetAddressValidator(false, true))
        .setDefaultValue(ModelNode.ZERO)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition BIND_PORT = new SimpleAttributeDefinitionBuilder("bind-port", ModelType.INT)
        .setRequired(false)
        .setAllowExpression(true)
        .setValidator(new IntRangeValidator(0, 65535, false, true))
        .setRestartAllServices()
        .build();

    static final Collection<AttributeDefinition> ATTRIBUTES = Collections.unmodifiableList(Arrays.asList( MATCH, BIND_ADDRESS, BIND_PORT));

    static final String RESOURCE_NAME = "outbound-bind-address";
    static final PathElement PATH = PathElement.pathElement(RESOURCE_NAME);
    static final OutboundBindAddressResourceDefinition INSTANCE = new OutboundBindAddressResourceDefinition();

    private OutboundBindAddressResourceDefinition() {
        super(new SimpleResourceDefinition.Parameters(PATH, IOSubsystemRegistrar.RESOLVER.createChildResolver(PATH))
            .setAddHandler(new OutboundBindAddressAddHandler())
            .setRemoveHandler(new OutboundBindAddressRemoveHandler())
        );
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return ATTRIBUTES;
    }

    public static OutboundBindAddressResourceDefinition getInstance() {
        return INSTANCE;
    }
}
