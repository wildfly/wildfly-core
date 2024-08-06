/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.discovery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.registry.AttributeAccess.Flag;
import org.jboss.dmr.ModelNode;
import org.wildfly.discovery.impl.AggregateDiscoveryProvider;
import org.wildfly.discovery.spi.DiscoveryProvider;
import org.wildfly.subsystem.resource.ResourceDescriptor;
import org.wildfly.subsystem.resource.capability.CapabilityReference;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

/**
 * Registers the aggregate discovery provider resource definition.
 * @author Paul Ferraro
 */
public class AggregateDiscoveryProviderRegistrar extends DiscoveryProviderRegistrar {

    static final PathElement PATH = PathElement.pathElement("aggregate-provider");

    private static final StringListAttributeDefinition PROVIDER_NAMES = new StringListAttributeDefinition.Builder("providers")
            .setCapabilityReference(CapabilityReference.builder(DISCOVERY_PROVIDER_CAPABILITY, DISCOVERY_PROVIDER_DESCRIPTOR).build())
            .setFlags(Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final Collection<AttributeDefinition> ATTRIBUTES = List.of(PROVIDER_NAMES);

    AggregateDiscoveryProviderRegistrar() {
        super(PATH, ResourceDescriptor.builder(DiscoverySubsystemRegistrar.RESOLVER.createChildResolver(PATH)).addAttributes(ATTRIBUTES));
    }

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        List<String> providers = PROVIDER_NAMES.unwrap(context, model);
        List<ServiceDependency<DiscoveryProvider>> dependencies = new ArrayList<>(providers.size());
        for (String provider : providers) {
            dependencies.add(ServiceDependency.on(DISCOVERY_PROVIDER_DESCRIPTOR, provider));
        }
        Supplier<DiscoveryProvider> factory = new Supplier<>() {
            @Override
            public DiscoveryProvider get() {
                return new AggregateDiscoveryProvider(dependencies.stream().map(Supplier::get).toArray(DiscoveryProvider[]::new));
            }
        };
        return CapabilityServiceInstaller.builder(DISCOVERY_PROVIDER_CAPABILITY, factory)
                .requires(dependencies)
                .build();
    }
}
