/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.stability;

import java.util.EnumSet;
import java.util.Map;

import org.jboss.as.controller.Feature;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.persistence.xml.ResourceRegistrationXMLElement;
import org.jboss.as.controller.persistence.xml.ResourceXMLParticleFactory;
import org.jboss.as.controller.persistence.xml.SubsystemResourceRegistrationXMLElement;
import org.jboss.as.controller.persistence.xml.SubsystemResourceXMLSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.as.version.Stability;
import org.jboss.staxmapper.IntVersion;

/**
 * @author Paul Ferraro
 */
public enum FooSubsystemSchema implements SubsystemResourceXMLSchema<FooSubsystemSchema> {
    VERSION_1_0(1),
    VERSION_1_0_PREVIEW(1, Stability.PREVIEW),
    VERSION_1_0_EXPERIMENTAL(1, Stability.EXPERIMENTAL),
    ;
    static final Map<Stability, FooSubsystemSchema> CURRENT = Feature.map(EnumSet.of(VERSION_1_0, VERSION_1_0_PREVIEW, VERSION_1_0_EXPERIMENTAL));

    private final VersionedNamespace<IntVersion, FooSubsystemSchema> namespace;
    private final ResourceXMLParticleFactory factory = ResourceXMLParticleFactory.newInstance(this);

    FooSubsystemSchema(int major) {
        this(major, Stability.DEFAULT);
    }

    FooSubsystemSchema(int major, Stability stability) {
        this.namespace = SubsystemSchema.createSubsystemURN(FooSubsystemResourceDefinition.REGISTRATION.getName(), stability, new IntVersion(major));
    }

    @Override
    public VersionedNamespace<IntVersion, FooSubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public SubsystemResourceRegistrationXMLElement getSubsystemXMLElement() {
        ResourceRegistrationXMLElement barElement = this.factory.namedElement(BarResourceDefinition.REGISTRATION)
                .addAttribute(BarResourceDefinition.TYPE)
                .build();
        return this.factory.subsystemElement(FooSubsystemResourceDefinition.REGISTRATION)
                .addAttributes(FooSubsystemResourceDefinition.ATTRIBUTES)
                .withContent(this.factory.choice().withCardinality(XMLCardinality.Unbounded.REQUIRED).addElement(barElement).build())
                .build();
    }
}
