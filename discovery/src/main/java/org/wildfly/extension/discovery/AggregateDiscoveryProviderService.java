/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.msc.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.discovery.impl.AggregateDiscoveryProvider;
import org.wildfly.discovery.spi.DiscoveryProvider;

/**
 * Service that provides an aggregate discovery provider.
 * @author Paul Ferraro
 */
class AggregateDiscoveryProviderService implements Service {
    private final Consumer<DiscoveryProvider> provider;
    private final List<Supplier<DiscoveryProvider>> providers;

    AggregateDiscoveryProviderService(Consumer<DiscoveryProvider> provider, List<Supplier<DiscoveryProvider>> providers) {
        this.provider = provider;
        this.providers = providers;
    }

    @Override
    public void start(StartContext context) throws StartException {
        DiscoveryProvider[] providers = new DiscoveryProvider[this.providers.size()];
        for (int i = 0; i < providers.length; ++i) {
            providers[i] = this.providers.get(i).get();
        }
        this.provider.accept(new AggregateDiscoveryProvider(providers));
    }

    @Override
    public void stop(StopContext context) {
        this.provider.accept(null);
    }
}
