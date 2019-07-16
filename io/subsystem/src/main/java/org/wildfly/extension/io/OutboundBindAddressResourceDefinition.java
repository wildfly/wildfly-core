/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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

    static final OutboundBindAddressResourceDefinition INSTANCE = new OutboundBindAddressResourceDefinition();

    private OutboundBindAddressResourceDefinition() {
        super(new SimpleResourceDefinition.Parameters(PathElement.pathElement(RESOURCE_NAME), IOExtension.getResolver(RESOURCE_NAME))
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
