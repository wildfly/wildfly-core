/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

package org.wildfly.extension.discovery;

import static org.wildfly.extension.discovery.DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.ServiceRemoveStepHandler;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.registry.AttributeAccess.Flag;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * Definition for aggregate discovery provider resources.
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:paul.ferraro@redhat.com">Paul Ferraro</a>
 */
final class AggregateDiscoveryProviderDefinition extends SimpleResourceDefinition {

    static final PathElement PATH = PathElement.pathElement("aggregate-provider");

    static final StringListAttributeDefinition PROVIDER_NAMES = new StringListAttributeDefinition.Builder("providers")
        .setCapabilityReference(DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY.getName())
        .setFlags(Flag.RESTART_RESOURCE_SERVICES)
        .build();

    private static final AbstractAddStepHandler ADD_HANDLER = new AggregateDiscoveryProviderAddHandler();

    AggregateDiscoveryProviderDefinition() {
        super(new Parameters(PATH, DiscoveryExtension.getResourceDescriptionResolver(PATH.getKey()))
            .setAddHandler(ADD_HANDLER)
            .setRemoveHandler(new ServiceRemoveStepHandler(DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY.getCapabilityServiceName(), ADD_HANDLER))
            .setCapabilities(DISCOVERY_PROVIDER_CAPABILITY));
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(PROVIDER_NAMES, null, new ReloadRequiredWriteAttributeHandler(PROVIDER_NAMES));
    }
}
