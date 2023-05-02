/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.experimental;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLDescription.PersistentResourceXMLBuilder;
import org.jboss.as.controller.PersistentSubsystemSchema;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.as.version.FeatureStream;
import org.jboss.staxmapper.IntVersion;

/**
 * @author Paul Ferraro
 */
public enum ExperimentalSubsystemSchema implements PersistentSubsystemSchema<ExperimentalSubsystemSchema> {
    VERSION_1_0_STABLE(1, FeatureStream.STABLE),
    VERSION_1_0_PREVIEW(1, FeatureStream.PREVIEW),
    VERSION_1_0_EXPERIMENTAL(1, FeatureStream.EXPERIMENTAL),
    ;
    private final VersionedNamespace<IntVersion, ExperimentalSubsystemSchema> namespace;

    ExperimentalSubsystemSchema(int major, FeatureStream stream) {
        this.namespace = SubsystemSchema.createSubsystemURN(ExperimentalSubsystemExtension.SUBSYSTEM_NAME, stream, new IntVersion(major));
    }

    @Override
    public VersionedNamespace<IntVersion, ExperimentalSubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public PersistentResourceXMLDescription getXMLDescription() {
        PersistentResourceXMLBuilder builder = builder(ExperimentalSubsystemResourceDefinition.PATH, this.namespace);
        if (this.namespace.getFeatureStream().enables(FeatureStream.EXPERIMENTAL)) {
            builder.addAttribute(ExperimentalSubsystemResourceDefinition.EXPERIMENTAL);
        }
        if (this.namespace.getFeatureStream().enables(FeatureStream.PREVIEW)) {
            builder.addChild(builder(PreviewResourceDefinition.PATH));
        }
        return builder.build();
    }
}
