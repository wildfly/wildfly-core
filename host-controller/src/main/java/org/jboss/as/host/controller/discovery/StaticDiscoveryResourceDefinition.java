/*
* JBoss, Home of Professional Open Source.
* Copyright 2013, Red Hat Middleware LLC, and individual contributors
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
        super(PathElement.pathElement(STATIC_DISCOVERY), HostResolver.getResolver(STATIC_DISCOVERY),
                new StaticDiscoveryAddHandler(hostControllerInfo),
                new StaticDiscoveryRemoveHandler(),
                OperationEntry.Flag.RESTART_ALL_SERVICES, OperationEntry.Flag.RESTART_ALL_SERVICES);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        super.registerAttributes(resourceRegistration);
        for (final SimpleAttributeDefinition attribute : STATIC_DISCOVERY_ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attribute, null, new StaticDiscoveryWriteAttributeHandler(attribute));
        }
    }
}
