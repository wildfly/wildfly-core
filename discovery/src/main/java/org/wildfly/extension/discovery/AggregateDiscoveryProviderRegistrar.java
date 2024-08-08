/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.discovery;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.dmr.ModelNode;
import org.wildfly.discovery.impl.AggregateDiscoveryProvider;
import org.wildfly.discovery.spi.DiscoveryProvider;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.capability.CapabilityReference;
import org.wildfly.subsystem.resource.capability.CapabilityReferenceListAttributeDefinition;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

/**
 * Registers the aggregate discovery provider resource definition.
 * @author Paul Ferraro
 */
public class AggregateDiscoveryProviderRegistrar extends DiscoveryProviderRegistrar {

    static final PathElement PATH = PathElement.pathElement("aggregate-provider");

    private static final CapabilityReferenceListAttributeDefinition<DiscoveryProvider> PROVIDER_NAMES = new CapabilityReferenceListAttributeDefinition.Builder<>("providers", CapabilityReference.builder(DISCOVERY_PROVIDER_CAPABILITY, DISCOVERY_PROVIDER_DESCRIPTOR).build()).build();

    static final Collection<AttributeDefinition> ATTRIBUTES = List.of(PROVIDER_NAMES);

    AggregateDiscoveryProviderRegistrar() {
        super(PATH, ResourceDescriptor.builder(DiscoverySubsystemRegistrar.RESOLVER.createChildResolver(PATH)).addAttributes(ATTRIBUTES));
    }

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        List<ServiceDependency<DiscoveryProvider>> providers = PROVIDER_NAMES.resolve(context, model);
        Supplier<DiscoveryProvider> factory = new Supplier<>() {
            @Override
            public DiscoveryProvider get() {
                return new AggregateDiscoveryProvider(providers.stream().map(Supplier::get).toArray(DiscoveryProvider[]::new));
            }
        };
        return CapabilityServiceInstaller.builder(DISCOVERY_PROVIDER_CAPABILITY, factory)
                .requires(providers)
                .build();
    }
}
