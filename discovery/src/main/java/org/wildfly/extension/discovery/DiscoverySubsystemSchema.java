/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import org.jboss.as.controller.SubsystemSchema;
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
        this.namespace = SubsystemSchema.createLegacySubsystemURN(DiscoverySubsystemResourceRegistrar.INSTANCE.getName(), new IntVersion(major, minor));
    }

    @Override
    public VersionedNamespace<IntVersion, DiscoverySubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public SubsystemResourceRegistrationXMLElement getSubsystemXMLElement() {
        ResourceXMLChoice providers = this.factory.choice().withCardinality(XMLCardinality.Unbounded.OPTIONAL)
                .addElement(this.factory.element(DiscoveryProviderResourceRegistration.STATIC).withContent(this.factory.sequence().addElement(StaticDiscoveryProviderResourceRegistrar.SERVICES).build()).build())
                .addElement(this.factory.element(DiscoveryProviderResourceRegistration.AGGREGATE).addAttribute(AggregateDiscoveryProviderResourceRegistrar.PROVIDER_NAMES).build())
                .build();
        return this.factory.element(DiscoverySubsystemResourceRegistrar.INSTANCE).withContent(providers).build();
    }
}
