/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.discovery;

import static org.wildfly.extension.discovery.StaticDiscoveryProviderRegistrar.ABSTRACT_TYPE;
import static org.wildfly.extension.discovery.StaticDiscoveryProviderRegistrar.ABSTRACT_TYPE_AUTHORITY;
import static org.wildfly.extension.discovery.StaticDiscoveryProviderRegistrar.NAME;
import static org.wildfly.extension.discovery.StaticDiscoveryProviderRegistrar.SERVICES;
import static org.wildfly.extension.discovery.StaticDiscoveryProviderRegistrar.SERVICE_ATTRIBUTES;
import static org.wildfly.extension.discovery.StaticDiscoveryProviderRegistrar.URI;
import static org.wildfly.extension.discovery.StaticDiscoveryProviderRegistrar.URI_SCHEME_AUTHORITY;
import static org.wildfly.extension.discovery.StaticDiscoveryProviderRegistrar.VALUE;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.wildfly.discovery.AttributeValue;
import org.wildfly.discovery.ServiceURL;
import org.wildfly.discovery.impl.StaticDiscoveryProvider;
import org.wildfly.subsystem.service.ResourceServiceConfigurator;
import org.wildfly.subsystem.service.ResourceServiceInstaller;
import org.wildfly.subsystem.service.capability.CapabilityServiceInstaller;

/**
 * Configures a static discovery provider service.
 * @author Paul Ferraro
 */
public class StaticDiscoveryProviderServiceConfigurator implements ResourceServiceConfigurator {

    @Override
    public ResourceServiceInstaller configure(OperationContext context, ModelNode model) throws OperationFailedException {
        List<ModelNode> services = SERVICES.resolveModelAttribute(context, model).asListOrEmpty();
        List<ServiceURL> serviceURLs = new ArrayList<>(services.size());
        for (ModelNode service : services) {
            ServiceURL.Builder builder = new ServiceURL.Builder();
            builder.setUri(java.net.URI.create(URI.resolveModelAttribute(context, service).asString()));
            String abstractType = ABSTRACT_TYPE.resolveModelAttribute(context, service).asStringOrNull();
            if (abstractType != null) {
                builder.setAbstractType(abstractType);
            }
            String abstractTypeAuthority = ABSTRACT_TYPE_AUTHORITY.resolveModelAttribute(context, service).asStringOrNull();
            if (abstractTypeAuthority != null) {
                builder.setAbstractTypeAuthority(abstractTypeAuthority);
            }
            String uriSchemeAuthority = URI_SCHEME_AUTHORITY.resolveModelAttribute(context, service).asStringOrNull();
            if (uriSchemeAuthority != null) {
                builder.setUriSchemeAuthority(uriSchemeAuthority);
            }
            for (ModelNode attribute : SERVICE_ATTRIBUTES.resolveModelAttribute(context, service).asListOrEmpty()) {
                String name = NAME.resolveModelAttribute(context, attribute).asString();
                String value = VALUE.resolveModelAttribute(context, attribute).asStringOrNull();
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
        return CapabilityServiceInstaller.builder(DiscoveryProviderRegistrar.DISCOVERY_PROVIDER_CAPABILITY, new StaticDiscoveryProvider(serviceURLs)).build();
    }
}
