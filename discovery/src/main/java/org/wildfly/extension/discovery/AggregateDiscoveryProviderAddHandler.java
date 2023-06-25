/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceController;
import org.wildfly.discovery.spi.DiscoveryProvider;

/**
 * Add operation handler for aggregate discovery provider resources.
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:paul.ferraro@redhat.com">Paul Ferraro</a>
 */
class AggregateDiscoveryProviderAddHandler extends AbstractAddStepHandler {

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        CapabilityServiceBuilder<?> builder = context.getCapabilityServiceTarget().addService();
        Consumer<DiscoveryProvider> provider = builder.provides(DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY);

        List<String> providerNames = AggregateDiscoveryProviderDefinition.PROVIDER_NAMES.unwrap(context, resource.getModel());
        List<Supplier<DiscoveryProvider>> providers = new ArrayList<>(providerNames.size());
        for (String providerName : providerNames) {
            providers.add(builder.requiresCapability(DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY.getName(), DiscoveryProvider.class, providerName));
        }
        builder.setInstance(new AggregateDiscoveryProviderService(provider, providers))
                .setInitialMode(ServiceController.Mode.ON_DEMAND)
                .install();
    }

    @Override
    protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {
        context.removeService(DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY.getCapabilityServiceName(context.getCurrentAddress()));
    }
}
