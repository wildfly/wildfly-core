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
import java.util.function.Consumer;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceController;
import org.wildfly.discovery.AttributeValue;
import org.wildfly.discovery.ServiceURL;
import org.wildfly.discovery.impl.StaticDiscoveryProvider;
import org.wildfly.discovery.spi.DiscoveryProvider;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:paul.ferraro@redhat.com">Paul Ferraro</a>
 */
class StaticDiscoveryProviderAddHandler extends AbstractAddStepHandler {

    StaticDiscoveryProviderAddHandler() {
        super(new Parameters().addAttribute(StaticDiscoveryProviderDefinition.SERVICES));
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        List<ModelNode> services = StaticDiscoveryProviderDefinition.SERVICES.resolveModelAttribute(context, resource.getModel()).asListOrEmpty();
        List<ServiceURL> serviceURLs = new ArrayList<>(services.size());
        for (ModelNode service : services) {
            ServiceURL.Builder builder = new ServiceURL.Builder();
            builder.setUri(URI.create(StaticDiscoveryProviderDefinition.URI.resolveModelAttribute(context, service).asString()));
            String abstractType = StaticDiscoveryProviderDefinition.ABSTRACT_TYPE.resolveModelAttribute(context, service).asStringOrNull();
            if (abstractType != null) {
                builder.setAbstractType(abstractType);
            }
            String abstractTypeAuthority = StaticDiscoveryProviderDefinition.ABSTRACT_TYPE_AUTHORITY.resolveModelAttribute(context, service).asStringOrNull();
            if (abstractTypeAuthority != null) {
                builder.setAbstractTypeAuthority(abstractTypeAuthority);
            }
            String uriSchemeAuthority = StaticDiscoveryProviderDefinition.URI_SCHEME_AUTHORITY.resolveModelAttribute(context, service).asStringOrNull();
            if (uriSchemeAuthority != null) {
                builder.setUriSchemeAuthority(uriSchemeAuthority);
            }
            List<ModelNode> attributes = StaticDiscoveryProviderDefinition.ATTRIBUTES.resolveModelAttribute(context, service).asListOrEmpty();
            for (ModelNode attribute : attributes) {
                String name = StaticDiscoveryProviderDefinition.NAME.resolveModelAttribute(context, attribute).asString();
                String value = StaticDiscoveryProviderDefinition.VALUE.resolveModelAttribute(context, attribute).asStringOrNull();
                if (value != null) {
                    builder.addAttribute(name, AttributeValue.fromString(value));
                } else {
                    builder.addAttribute(name);
                }
            }
            ServiceURL serviceURL = builder.create();
            Messages.log.tracef("Adding service URL %s", serviceURL);
            serviceURLs.add(serviceURL);
        }

        CapabilityServiceBuilder<?> builder = context.getCapabilityServiceTarget().addService();
        Consumer<DiscoveryProvider> provider = builder.provides(DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY);
        builder.setInstance(Service.newInstance(provider, new StaticDiscoveryProvider(serviceURLs)))
            .setInitialMode(ServiceController.Mode.ON_DEMAND)
            .install();
    }

    @Override
    protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {
        context.removeService(DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY.getCapabilityServiceName(context.getCurrentAddress()));
    }
}
