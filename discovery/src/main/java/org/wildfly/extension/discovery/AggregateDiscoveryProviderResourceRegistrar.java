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
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.ServiceDependency;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

/**
 * Registers the aggregate discovery provider resource definition.
 * @author Paul Ferraro
 */
public class AggregateDiscoveryProviderResourceRegistrar extends DiscoveryProviderResourceRegistrar {

    AggregateDiscoveryProviderResourceRegistrar() {
        super(AggregateDiscoveryProviderResourceDescription.INSTANCE);
    }

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        ServiceDependency<DiscoveryProvider> provider = AggregateDiscoveryProviderResourceDescription.PROVIDER_NAMES.resolve(context, model).map(new Function<>() {
            @Override
            public DiscoveryProvider apply(List<DiscoveryProvider> providers) {
                return new AggregateDiscoveryProvider(providers.toArray(DiscoveryProvider[]::new));
            }
        });
        return CapabilityServiceInstaller.builder(DISCOVERY_PROVIDER_CAPABILITY, provider).build();
    }
}
