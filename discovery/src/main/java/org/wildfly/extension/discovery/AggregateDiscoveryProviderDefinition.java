/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
        super(new Parameters(PATH, DiscoveryExtension.SUBSYSTEM_RESOLVER.createChildResolver(PATH))
            .setAddHandler(ADD_HANDLER)
            .setRemoveHandler(new ServiceRemoveStepHandler(DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY.getCapabilityServiceName(), ADD_HANDLER))
            .setCapabilities(DISCOVERY_PROVIDER_CAPABILITY));
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(PROVIDER_NAMES, null, new ReloadRequiredWriteAttributeHandler(PROVIDER_NAMES));
    }
}
