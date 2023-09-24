/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import java.util.function.Function;

import org.jboss.as.controller.PersistentResourceXMLDescription;

/**
 * XML description factory for the discovery subsystem.
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:paul.ferraro@redhat.com">Paul Ferraro</a>
 */
public enum DiscoverySubsystemXMLDescriptionFactory implements Function<DiscoverySubsystemSchema, PersistentResourceXMLDescription> {
    INSTANCE;

    @Override
    public PersistentResourceXMLDescription apply(DiscoverySubsystemSchema schema) {
        return builder(DiscoverySubsystemDefinition.PATH, schema.getNamespace())
                .addChild(builder(StaticDiscoveryProviderDefinition.PATH).addAttribute(StaticDiscoveryProviderDefinition.SERVICES))
                .addChild(builder(AggregateDiscoveryProviderDefinition.PATH).addAttribute(AggregateDiscoveryProviderDefinition.PROVIDER_NAMES))
                .build();
    }
}
