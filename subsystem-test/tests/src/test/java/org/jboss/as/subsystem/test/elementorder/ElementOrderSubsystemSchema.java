/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.elementorder;

import org.jboss.as.controller.AttributeMarshallers;
import org.jboss.as.controller.AttributeParsers;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentSubsystemSchema;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.staxmapper.IntVersion;

public enum ElementOrderSubsystemSchema implements PersistentSubsystemSchema<ElementOrderSubsystemSchema> {
    VERSION_1_0;
    private final VersionedNamespace<IntVersion, ElementOrderSubsystemSchema> namespace = SubsystemSchema.createSubsystemURN(ElementOrderSubsystemResourceDefinition.SUBSYSTEM_NAME, new IntVersion(1));

    @Override
    public VersionedNamespace<IntVersion, ElementOrderSubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public PersistentResourceXMLDescription getXMLDescription() {
        PersistentResourceXMLDescription.Factory factory = PersistentResourceXMLDescription.factory(this);
        PersistentResourceXMLDescription.Builder builder = factory.builder(ElementOrderSubsystemResourceDefinition.PATH);
        PersistentResourceXMLDescription.Builder groupBuilder = factory.builder(ElementOrderSubsystemResourceDefinition.GROUP_REGISTRATION);
        groupBuilder.addAttribute(ElementOrderSubsystemResourceDefinition.ATTRIBUTE, AttributeParsers.SIMPLE_ELEMENT, AttributeMarshallers.SIMPLE_ELEMENT);
        groupBuilder.addAttribute(ElementOrderSubsystemResourceDefinition.ATTRIBUTE_2, AttributeParsers.SIMPLE_ELEMENT, AttributeMarshallers.SIMPLE_ELEMENT);
        groupBuilder.addChild(factory.builder(ElementOrderSubsystemResourceDefinition.CHILD_REGISTRATION).build());
        groupBuilder.addChild(factory.builder(ElementOrderSubsystemResourceDefinition.CHILD_2_REGISTRATION).build());
        builder.addChild(groupBuilder.build());
        return builder.build();
    }
}
