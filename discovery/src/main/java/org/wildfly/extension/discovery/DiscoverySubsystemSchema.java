/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import java.util.List;

import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.persistence.xml.ResourceXMLElement;
import org.jboss.as.controller.persistence.xml.SubsystemResourceXMLSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.as.controller.xml.XMLChoice;
import org.jboss.staxmapper.IntVersion;

/**
 * Enumeration of discovery subsystem schema versions.
 * @author Paul Ferraro
 */
enum DiscoverySubsystemSchema implements SubsystemResourceXMLSchema<DiscoverySubsystemSchema> {
    VERSION_1_0(1, 0),
    ;
    static final DiscoverySubsystemSchema CURRENT = VERSION_1_0;

    private final VersionedNamespace<IntVersion, DiscoverySubsystemSchema> namespace;

    DiscoverySubsystemSchema(int major, int minor) {
        this.namespace = SubsystemSchema.createLegacySubsystemURN(DiscoverySubsystemResourceDescription.INSTANCE.getName(), new IntVersion(major, minor));
    }

    @Override
    public VersionedNamespace<IntVersion, DiscoverySubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public ResourceXMLElement getSubsystemResourceXMLElement() {
        ResourceXMLElement.Builder.Factory factory = ResourceXMLElement.Builder.Factory.newInstance(this);
        return factory.createBuilder(DiscoverySubsystemResourceDescription.INSTANCE)
                .appendChild(XMLChoice.of(List.of(factory.createBuilder(StaticDiscoveryProviderResourceDescription.INSTANCE).build(), factory.createBuilder(AggregateDiscoveryProviderResourceDescription.INSTANCE).build()), XMLCardinality.Unbounded.OPTIONAL))
                .build();
    }
}
