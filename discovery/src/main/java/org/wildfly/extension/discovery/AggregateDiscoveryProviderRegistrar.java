/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.discovery;

import java.util.Collection;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.registry.AttributeAccess.Flag;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.capability.CapabilityReferenceRecorder;
import org.wildfly.subsystem.resource.operation.ResourceOperationRuntimeHandler;

/**
 * @author Paul Ferraro
 */
public class AggregateDiscoveryProviderRegistrar extends DiscoveryProviderRegistrar {

    static final PathElement PATH = PathElement.pathElement("aggregate-provider");

    static final StringListAttributeDefinition PROVIDER_NAMES = new StringListAttributeDefinition.Builder("providers")
        .setCapabilityReference(CapabilityReferenceRecorder.of(DISCOVERY_PROVIDER_CAPABILITY, DISCOVERY_PROVIDER_DESCRIPTOR))
        .setFlags(Flag.RESTART_RESOURCE_SERVICES)
        .build();

    static final Collection<AttributeDefinition> ATTRIBUTES = List.of(PROVIDER_NAMES);

    AggregateDiscoveryProviderRegistrar() {
        super(PATH, ResourceDescriptor.builder(DiscoverySubsystemRegistrar.RESOLVER.createChildResolver(PATH))
                .addAttributes(ATTRIBUTES)
                .withRuntimeHandler(ResourceOperationRuntimeHandler.configureService(new AggregateDiscoveryProviderServiceConfigurator())));
    }
}
