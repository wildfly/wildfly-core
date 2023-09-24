/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.host.controller.discovery;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STATIC_DISCOVERY;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.host.controller.descriptions.HostResolver;
import org.jboss.as.host.controller.operations.DomainControllerWriteAttributeHandler;
import org.jboss.as.host.controller.operations.LocalHostControllerInfoImpl;
import org.jboss.as.host.controller.operations.StaticDiscoveryAddHandler;
import org.jboss.as.host.controller.operations.StaticDiscoveryRemoveHandler;
import org.jboss.as.host.controller.operations.StaticDiscoveryWriteAttributeHandler;

/**
 * {@link org.jboss.as.controller.ResourceDefinition} for a resource representing a static discovery option.
 *
 * @author Farah Juma
 */
public class StaticDiscoveryResourceDefinition extends SimpleResourceDefinition {

    public static final SimpleAttributeDefinition HOST = getRequiredCopy(DomainControllerWriteAttributeHandler.HOST);

    public static final SimpleAttributeDefinition PORT = getRequiredCopy(DomainControllerWriteAttributeHandler.PORT);

    public static final SimpleAttributeDefinition PROTOCOL = DomainControllerWriteAttributeHandler.PROTOCOL; // protocol should allow null it appears

    private static SimpleAttributeDefinition getRequiredCopy(SimpleAttributeDefinition attr) {
        return new SimpleAttributeDefinitionBuilder(attr)
        .setRequired(true)
        .build();
    }

    public static final SimpleAttributeDefinition[] STATIC_DISCOVERY_ATTRIBUTES = new SimpleAttributeDefinition[] {PROTOCOL, HOST, PORT};

    public StaticDiscoveryResourceDefinition(final LocalHostControllerInfoImpl hostControllerInfo) {
        super(new Parameters(PathElement.pathElement(STATIC_DISCOVERY), HostResolver.getResolver(STATIC_DISCOVERY))
                .setAddHandler(new StaticDiscoveryAddHandler(hostControllerInfo))
                .setRemoveHandler(new StaticDiscoveryRemoveHandler())
                .setAddRestartLevel(OperationEntry.Flag.RESTART_ALL_SERVICES));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        for (final SimpleAttributeDefinition attribute : STATIC_DISCOVERY_ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attribute, null, new StaticDiscoveryWriteAttributeHandler(attribute));
        }
    }
}
