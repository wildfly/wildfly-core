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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.wildfly.discovery.spi.DiscoveryProvider;

/**
 * Add operation handler for aggregate discovery provider resources.
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:paul.ferraro@redhat.com">Paul Ferraro</a>
 */
class AggregateDiscoveryProviderAddHandler extends AbstractAddStepHandler {

    AggregateDiscoveryProviderAddHandler() {
        super(new Parameters().addAttribute(AggregateDiscoveryProviderDefinition.PROVIDER_NAMES));
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        ServiceName name = DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY.getCapabilityServiceName(context.getCurrentAddress());
        ServiceBuilder<?> builder = context.getCapabilityServiceTarget().addService(name);
        Consumer<DiscoveryProvider> provider = builder.provides(name);

        List<String> providerNames = AggregateDiscoveryProviderDefinition.PROVIDER_NAMES.unwrap(context, resource.getModel());
        List<Supplier<DiscoveryProvider>> providers = new ArrayList<>(providerNames.size());
        for (String providerName : providerNames) {
            providers.add(builder.requires(context.getCapabilityServiceName(DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY.getName(), DiscoveryProvider.class, providerName)));
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
