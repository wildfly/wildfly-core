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

import static org.wildfly.extension.discovery.DiscoveryExtension.AGGREGATE_PROVIDER;
import static org.wildfly.extension.discovery.DiscoveryExtension.DISCOVERY_PROVIDER_RUNTIME_CAPABILITY;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class AggregateProviderDefinition extends SimpleResourceDefinition {

    static final StringListAttributeDefinition PROVIDER_NAMES = new StringListAttributeDefinition.Builder(DiscoveryExtension.PROVIDERS)
        .setCapabilityReference(DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY)
        .build();

    AggregateProviderDefinition() {
        super(new Parameters(PathElement.pathElement(AGGREGATE_PROVIDER), DiscoveryExtension.getResourceDescriptionResolver(AGGREGATE_PROVIDER))
            .setAddHandler(AggregateProviderAddHandler.getInstance())
            .setRemoveHandler(new TrivialRemoveStepHandler(DISCOVERY_PROVIDER_RUNTIME_CAPABILITY))
            .setCapabilities(DISCOVERY_PROVIDER_RUNTIME_CAPABILITY));
    }

    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(PROVIDER_NAMES, null, AggregateProviderAddHandler::modifyRegistrationModel);
    }

    private static final ResourceDefinition INSTANCE = new AggregateProviderDefinition();

    static ResourceDefinition getInstance() {
        return INSTANCE;
    }
}
