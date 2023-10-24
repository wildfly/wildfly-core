/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.quality;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import java.util.EnumSet;
import java.util.Map;

import org.jboss.as.controller.Feature;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLDescription.PersistentResourceXMLBuilder;
import org.jboss.as.controller.PersistentSubsystemSchema;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.as.version.Quality;
import org.jboss.staxmapper.IntVersion;

/**
 * @author Paul Ferraro
 */
public enum FooSubsystemSchema implements PersistentSubsystemSchema<FooSubsystemSchema> {
    VERSION_1_0(1),
    VERSION_1_0_PREVIEW(1, Quality.PREVIEW),
    VERSION_1_0_EXPERIMENTAL(1, Quality.EXPERIMENTAL),
    ;
    static final Map<Quality, FooSubsystemSchema> CURRENT = Feature.map(EnumSet.of(VERSION_1_0, VERSION_1_0_PREVIEW, VERSION_1_0_EXPERIMENTAL));

    private final VersionedNamespace<IntVersion, FooSubsystemSchema> namespace;

    FooSubsystemSchema(int major) {
        this(major, Quality.DEFAULT);
    }

    FooSubsystemSchema(int major, Quality quality) {
        this.namespace = SubsystemSchema.createSubsystemURN(FooSubsystemResourceDefinition.SUBSYSTEM_NAME, quality, new IntVersion(major));
    }

    @Override
    public VersionedNamespace<IntVersion, FooSubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public PersistentResourceXMLDescription getXMLDescription() {
        PersistentResourceXMLBuilder builder = builder(FooSubsystemResourceDefinition.PATH, this.namespace);
        builder.addAttributes(FooSubsystemResourceDefinition.ATTRIBUTES.stream().filter(this::enables));
        if (this.enables(BarResourceDefinition.PATH)) {
            builder.addChild(builder(BarResourceDefinition.PATH).addAttribute(BarResourceDefinition.TYPE));
        }
        return builder.build();
    }
}
