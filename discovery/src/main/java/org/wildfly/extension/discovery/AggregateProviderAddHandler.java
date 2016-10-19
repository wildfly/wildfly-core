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

import java.util.List;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.operations.global.WriteAttributeHandler;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.wildfly.discovery.impl.AggregateDiscoveryProvider;
import org.wildfly.discovery.impl.MutableDiscoveryProvider;
import org.wildfly.discovery.spi.DiscoveryProvider;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class AggregateProviderAddHandler extends AbstractAddStepHandler {
    private static final AggregateProviderAddHandler INSTANCE = new AggregateProviderAddHandler();

    static AggregateProviderAddHandler getInstance() {
        return INSTANCE;
    }

    private AggregateProviderAddHandler() {
        super(new Parameters().addAttribute(AggregateProviderDefinition.PROVIDER_NAMES));
    }

    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        context.registerCapability(
            RuntimeCapability.Builder
                .of(DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY, true, new MutableDiscoveryProvider())
                .build().fromBaseCapability(context.getCurrentAddressValue()));
        super.execute(context, operation);
    }

    protected void recordCapabilitiesAndRequirements(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {
        final AttributeDefinition ad = AggregateProviderDefinition.PROVIDER_NAMES;
        ad.addCapabilityRequirements(context, resource.getModel().get(ad.getName()));
    }

    static void modifyRegistrationModel(OperationContext context, ModelNode op) throws OperationFailedException {
        WriteAttributeHandler.INSTANCE.execute(context, op);
        context.addStep(op, AggregateProviderAddHandler::modifyRegistration, OperationContext.Stage.RUNTIME);
    }

    static void modifyRegistration(OperationContext context, ModelNode op) throws OperationFailedException {
        final MutableDiscoveryProvider mutableDiscoveryProvider = context.getCapabilityRuntimeAPI(DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY, context.getCurrentAddressValue(), MutableDiscoveryProvider.class);
        final ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
        if (model.hasDefined(DiscoveryExtension.PROVIDERS)) {
            final List<String> list = AggregateProviderDefinition.PROVIDER_NAMES.unwrap(context, model);
            if (list.isEmpty()) {
                mutableDiscoveryProvider.setDiscoveryProvider(DiscoveryProvider.EMPTY);
            } else {
                final DiscoveryProvider[] providers = new DiscoveryProvider[list.size()];
                int i = 0;
                for (String name : list) {
                    providers[i ++] = context.getCapabilityRuntimeAPI(DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY, name, DiscoveryProvider.class);
                }
                final DiscoveryProvider discoveryProvider = new AggregateDiscoveryProvider(providers);
                mutableDiscoveryProvider.setDiscoveryProvider(discoveryProvider);
            }
        } else {
            mutableDiscoveryProvider.setDiscoveryProvider(DiscoveryProvider.EMPTY);
        }
    }
}
