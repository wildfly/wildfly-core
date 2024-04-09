/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentSubsystemSchema;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.IntVersion;
import org.wildfly.io.OptionAttributeDefinition;

/**
 * Enumerates the supported schemas of the IO subsystem.
 */
public enum IOSubsystemSchema implements PersistentSubsystemSchema<IOSubsystemSchema> {
    VERSION_1_1(1, 1), // WildFly 8.1 - 10.1
    VERSION_2_0(2, 0), // WildFly 11 - 12
    VERSION_3_0(3, 0), // WildFly 13 - 31
    VERSION_4_0(4, 0), // WildFly 32-present
    ;
    static final IOSubsystemSchema CURRENT = VERSION_4_0;

    private final VersionedNamespace<IntVersion, IOSubsystemSchema> namespace;

    IOSubsystemSchema(int major, int minor) {
        this.namespace = SubsystemSchema.createLegacySubsystemURN(IOSubsystemRegistrar.NAME, new IntVersion(major, minor));
    }

    @Override
    public VersionedNamespace<IntVersion, IOSubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public PersistentResourceXMLDescription getXMLDescription() {
        PersistentResourceXMLDescription.Factory factory = PersistentResourceXMLDescription.factory(this);
        PersistentResourceXMLDescription.Builder builder = factory.builder(IOSubsystemRegistrar.PATH);
        if (this.since(VERSION_4_0)) {
            builder.addAttribute(IOSubsystemRegistrar.DEFAULT_WORKER);
        } else {
            builder.setAdditionalOperationsGenerator(new PersistentResourceXMLDescription.AdditionalOperationsGenerator() {
                @Override
                public void additionalOperations(PathAddress address, ModelNode addOperation, List<ModelNode> operations) {
                    // Apply "magic" default worker referenced by other subsystems
                    addOperation.get(IOSubsystemRegistrar.DEFAULT_WORKER.getName()).set(IOSubsystemRegistrar.LEGACY_DEFAULT_WORKER);
                }
            });
        }

        Stream<OptionAttributeDefinition> workerAttributes = Stream.of(WorkerResourceDefinition.ATTRIBUTES);
        if (!this.since(VERSION_3_0)) {
            workerAttributes = workerAttributes.filter(Predicate.not(WorkerResourceDefinition.WORKER_TASK_CORE_THREADS::equals));
        }
        PersistentResourceXMLDescription.Builder workerBuilder = factory.builder(WorkerResourceDefinition.PATH).addAttributes(workerAttributes);
        if (this.since(VERSION_2_0)) {
            workerBuilder.addChild(factory.builder(OutboundBindAddressResourceDefinition.PATH).addAttributes(OutboundBindAddressResourceDefinition.ATTRIBUTES.stream()).build());
        }

        return builder.addChild(workerBuilder.build())
                .addChild(factory.builder(BufferPoolResourceDefinition.PATH).addAttributes(BufferPoolResourceDefinition.ATTRIBUTES.stream()).build())
                .build();
    }
}
