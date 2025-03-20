/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.persistence.xml.ResourceRegistrationXMLElement;
import org.jboss.as.controller.persistence.xml.ResourceXMLChoice;
import org.jboss.as.controller.persistence.xml.ResourceXMLParticleFactory;
import org.jboss.as.controller.persistence.xml.SubsystemResourceRegistrationXMLElement;
import org.jboss.as.controller.persistence.xml.SubsystemResourceXMLSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.staxmapper.IntVersion;

/**
 * Enumeration of discovery subsystem schema versions.
 * @author Paul Ferraro
 */
enum DiscoverySubsystemSchema implements SubsystemResourceXMLSchema<DiscoverySubsystemSchema> {
    VERSION_1_0(1, 0),
    ;
    static final DiscoverySubsystemSchema CURRENT = VERSION_1_0;

    private final ResourceXMLParticleFactory factory = ResourceXMLParticleFactory.newInstance(this);
    private final VersionedNamespace<IntVersion, DiscoverySubsystemSchema> namespace;

    DiscoverySubsystemSchema(int major, int minor) {
        this.namespace = SubsystemSchema.createLegacySubsystemURN(DiscoverySubsystemResourceDefinitionRegistrar.REGISTRATION.getName(), new IntVersion(major, minor));
    }

    @Override
    public VersionedNamespace<IntVersion, DiscoverySubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public SubsystemResourceRegistrationXMLElement getSubsystemXMLElement() {
        ResourceXMLChoice providerChoices = this.factory.choice().withCardinality(XMLCardinality.Unbounded.OPTIONAL)
                .addElement(this.staticDiscoveryProviderElement())
                .addElement(this.aggregateDiscoveryProviderElement())
                .build();
        return this.factory.subsystemElement(DiscoverySubsystemResourceDefinitionRegistrar.REGISTRATION)
                .withContent(providerChoices)
                .build();
    }

    private ResourceRegistrationXMLElement staticDiscoveryProviderElement() {
        return this.factory.namedElement(StaticDiscoveryProviderResourceDefinitionRegistrar.REGISTRATION)
                .withContent(this.factory.sequence().addElement(StaticDiscoveryProviderResourceDefinitionRegistrar.SERVICES).build())
                .build();
    }

    private ResourceRegistrationXMLElement aggregateDiscoveryProviderElement() {
        return this.factory.namedElement(AggregateDiscoveryProviderResourceDefinitionRegistrar.REGISTRATION)
                .addAttribute(AggregateDiscoveryProviderResourceDefinitionRegistrar.PROVIDER_NAMES)
                .build();
    }
}