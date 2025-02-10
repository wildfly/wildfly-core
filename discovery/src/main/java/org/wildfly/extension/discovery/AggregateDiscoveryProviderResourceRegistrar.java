/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.discovery;

import java.util.List;
import java.util.function.Function;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.wildfly.discovery.impl.AggregateDiscoveryProvider;
import org.wildfly.discovery.spi.DiscoveryProvider;
import org.wildfly.subsystem.resource.capability.CapabilityReference;
import org.wildfly.subsystem.resource.capability.CapabilityReferenceListAttributeDefinition;
import org.wildfly.subsystem.service.ServiceDependency;

/**
 * Registers the aggregate discovery provider resource definition.
 * @author Paul Ferraro
 */
public class AggregateDiscoveryProviderResourceRegistrar extends DiscoveryProviderResourceRegistrar {

    static final CapabilityReferenceListAttributeDefinition<DiscoveryProvider> PROVIDER_NAMES = new CapabilityReferenceListAttributeDefinition.Builder<>("providers", CapabilityReference.builder(DiscoveryProviderResourceRegistrar.DISCOVERY_PROVIDER_CAPABILITY, DiscoveryProviderResourceRegistrar.DISCOVERY_PROVIDER_DESCRIPTOR).build()).build();

    AggregateDiscoveryProviderResourceRegistrar() {
        super(DiscoveryProviderResourceRegistration.AGGREGATE, List.of(PROVIDER_NAMES));
    }

    @Override
    public ServiceDependency<DiscoveryProvider> resolve(OperationContext context, ModelNode model) throws OperationFailedException {
        return PROVIDER_NAMES.resolve(context, model).map(new Function<>() {
            @Override
            public DiscoveryProvider apply(List<DiscoveryProvider> providers) {
                return new AggregateDiscoveryProvider(providers.toArray(DiscoveryProvider[]::new));
            }
        });
    }
}
