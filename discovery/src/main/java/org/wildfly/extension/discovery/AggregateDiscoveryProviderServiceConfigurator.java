/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.discovery;

import static org.wildfly.extension.discovery.AggregateDiscoveryProviderRegistrar.PROVIDER_NAMES;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.wildfly.discovery.impl.AggregateDiscoveryProvider;
import org.wildfly.discovery.spi.DiscoveryProvider;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

/**
 * Configures an aggregate discovery provider service.
 * @author Paul Ferraro
 */
public class AggregateDiscoveryProviderServiceConfigurator implements ResourceServiceConfigurator {

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        List<String> providers = PROVIDER_NAMES.unwrap(context, model);
        List<ServiceDependency<DiscoveryProvider>> dependencies = new ArrayList<>(providers.size());
        for (String provider : providers) {
            dependencies.add(ServiceDependency.on(DiscoveryProviderRegistrar.DISCOVERY_PROVIDER_DESCRIPTOR, provider));
        }
        Supplier<DiscoveryProvider> factory = new Supplier<>() {
            @Override
            public DiscoveryProvider get() {
                return new AggregateDiscoveryProvider(dependencies.stream().map(Supplier::get).toArray(DiscoveryProvider[]::new));
            }
        };
        return CapabilityServiceInstaller.builder(DiscoveryProviderRegistrar.DISCOVERY_PROVIDER_CAPABILITY, factory).requires(dependencies).build();
    }
}
