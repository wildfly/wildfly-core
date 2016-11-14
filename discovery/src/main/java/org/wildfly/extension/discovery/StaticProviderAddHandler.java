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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.operations.global.WriteAttributeHandler;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.wildfly.discovery.AttributeValue;
import org.wildfly.discovery.ServiceURL;
import org.wildfly.discovery.impl.MutableDiscoveryProvider;
import org.wildfly.discovery.impl.StaticDiscoveryProvider;
import org.wildfly.discovery.spi.DiscoveryProvider;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
class StaticProviderAddHandler extends AbstractAddStepHandler {
    private static final StaticProviderAddHandler INSTANCE = new StaticProviderAddHandler();

    static StaticProviderAddHandler getInstance() {
        return INSTANCE;
    }

    private StaticProviderAddHandler() {
        super(new Parameters().addAttribute(StaticProviderDefinition.SERVICES));
    }

    public void execute(final OperationContext context, final ModelNode operation) throws OperationFailedException {
        context.registerCapability(
            RuntimeCapability.Builder
                .of(DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY, true, new MutableDiscoveryProvider())
                .build().fromBaseCapability(context.getCurrentAddressValue()));
        super.execute(context, operation);
    }

    protected void recordCapabilitiesAndRequirements(final OperationContext context, final ModelNode operation, final Resource resource) throws OperationFailedException {
        // no operation
    }

    static void modifyRegistrationModel(OperationContext context, ModelNode op) throws OperationFailedException {
        WriteAttributeHandler.INSTANCE.execute(context, op);
        context.addStep(op, StaticProviderAddHandler::modifyRegistration, OperationContext.Stage.RUNTIME);
    }

    static void modifyRegistration(OperationContext context, ModelNode op) throws OperationFailedException {
        final MutableDiscoveryProvider mutableDiscoveryProvider = context.getCapabilityRuntimeAPI(DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY, context.getCurrentAddressValue(), MutableDiscoveryProvider.class);
        final ModelNode model = context.readResource(PathAddress.EMPTY_ADDRESS).getModel();
        if (model.hasDefined(DiscoveryExtension.SERVICES)) {
            final List<ModelNode> list = StaticProviderDefinition.SERVICES.resolveModelAttribute(context, model).asList();
            final List<ServiceURL> services = new ArrayList<>();
            for (ModelNode servicesNode : list) {
                final ModelNode abstractTypeNode = StaticProviderDefinition.ABSTRACT_TYPE.resolveModelAttribute(context, servicesNode);
                final ModelNode abstractTypeAuthorityNode = StaticProviderDefinition.ABSTRACT_TYPE_AUTHORITY.resolveModelAttribute(context, servicesNode);
                final ModelNode uriNode = StaticProviderDefinition.URI.resolveModelAttribute(context, servicesNode);
                final ModelNode uriSchemeAuthorityNode = StaticProviderDefinition.URI_SCHEME_AUTHORITY.resolveModelAttribute(context, servicesNode);
                final ServiceURL.Builder builder = new ServiceURL.Builder();
                if (abstractTypeNode != null && abstractTypeNode.isDefined()) builder.setAbstractType(abstractTypeNode.asString());
                if (abstractTypeAuthorityNode != null && abstractTypeAuthorityNode.isDefined()) builder.setAbstractTypeAuthority(abstractTypeAuthorityNode.asString());
                builder.setUri(URI.create(uriNode.asString()));
                if (uriSchemeAuthorityNode != null && uriSchemeAuthorityNode.isDefined()) builder.setUriSchemeAuthority(uriSchemeAuthorityNode.asString());
                final ModelNode attributesNode = StaticProviderDefinition.ATTRIBUTES.resolveModelAttribute(context, servicesNode);
                if (attributesNode != null && attributesNode.isDefined()) {
                    final List<ModelNode> attributeNodeList = attributesNode.asList();
                    if (! attributeNodeList.isEmpty()) {
                        for (ModelNode node : attributeNodeList) {
                            final String key = StaticProviderDefinition.NAME.resolveModelAttribute(context, node).asString();
                            final ModelNode valueNode = StaticProviderDefinition.VALUE.resolveModelAttribute(context, node);
                            if (valueNode != null) {
                                builder.addAttribute(key, AttributeValue.fromString(valueNode.asString()));
                            } else {
                                builder.addAttribute(key);
                            }
                        }
                    }
                }
                final ServiceURL serviceURL = builder.create();
                Messages.log.tracef("Adding service URL %s", serviceURL);
                services.add(serviceURL);
            }
            mutableDiscoveryProvider.setDiscoveryProvider(services.isEmpty() ? DiscoveryProvider.EMPTY : new StaticDiscoveryProvider(services));
        } else {
            mutableDiscoveryProvider.setDiscoveryProvider(DiscoveryProvider.EMPTY);
        }
    }
}
