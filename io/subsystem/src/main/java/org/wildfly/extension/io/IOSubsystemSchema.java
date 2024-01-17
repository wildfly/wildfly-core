/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentSubsystemSchema;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.staxmapper.IntVersion;

/**
 * Enumerates the supported schemas of the IO subsystem.
 */
public enum IOSubsystemSchema implements PersistentSubsystemSchema<IOSubsystemSchema> {
    VERSION_1_1(1, 1), // WildFly 8.1 - 10.1
    VERSION_2_0(2, 0), // WildFly 11 - 12
    VERSION_3_0(3, 0), // WildFly 13 - present
    ;
    static final IOSubsystemSchema CURRENT = VERSION_3_0;

    private final VersionedNamespace<IntVersion, IOSubsystemSchema> namespace;

    IOSubsystemSchema(int major, int minor) {
        this.namespace = SubsystemSchema.createLegacySubsystemURN(IOExtension.SUBSYSTEM_NAME, new IntVersion(major, minor));
    }

    @Override
    public VersionedNamespace<IntVersion, IOSubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public PersistentResourceXMLDescription getXMLDescription() {
        return builder(IOExtension.SUBSYSTEM_PATH, this.namespace)
                .addChild(this.workerBuilder())
                .addChild(builder(IOExtension.BUFFER_POOL_PATH).addAttributes(BufferPoolResourceDefinition.ATTRIBUTES.stream()))
                .build();
    }

    private PersistentResourceXMLDescription.PersistentResourceXMLBuilder workerBuilder() {
        Stream<OptionAttributeDefinition> workerAttributes = Stream.of(WorkerResourceDefinition.ATTRIBUTES);
        if (!this.since(VERSION_3_0)) {
            workerAttributes = workerAttributes.filter(Predicate.not(WorkerResourceDefinition.WORKER_TASK_CORE_THREADS::equals));
        }
        PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder = builder(IOExtension.WORKER_PATH).addAttributes(workerAttributes);
        if (this.since(VERSION_2_0)) {
            builder.addChild(builder(OutboundBindAddressResourceDefinition.PATH).addAttributes(OutboundBindAddressResourceDefinition.ATTRIBUTES.stream()));
        }
        return builder;
    }
}
