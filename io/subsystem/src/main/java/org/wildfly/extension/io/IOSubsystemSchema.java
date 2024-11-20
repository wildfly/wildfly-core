/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import java.util.List;
import java.util.function.UnaryOperator;

import org.jboss.as.controller.ResourceDescription;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.persistence.xml.ResourceXMLElement;
import org.jboss.as.controller.persistence.xml.SubsystemResourceXMLSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.IntVersion;

/**
 * Enumerates the supported schemas of the IO subsystem.
 */
public enum IOSubsystemSchema implements SubsystemResourceXMLSchema<IOSubsystemSchema> {
    VERSION_1_1(1, 1), // WildFly 8.1 - 10.1
    VERSION_2_0(2, 0), // WildFly 11 - 12
    VERSION_3_0(3, 0), // WildFly 13 - 31
    VERSION_4_0(4, 0), // WildFly 32-present
    ;
    static final IOSubsystemSchema CURRENT = VERSION_4_0;

    private final VersionedNamespace<IntVersion, IOSubsystemSchema> namespace;

    IOSubsystemSchema(int major, int minor) {
        this.namespace = SubsystemSchema.createLegacySubsystemURN(IOSubsystemResourceDescription.INSTANCE.getName(), new IntVersion(major, minor));
    }

    @Override
    public VersionedNamespace<IntVersion, IOSubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public ResourceXMLElement getSubsystemResourceXMLElement() {
        ResourceXMLElement.Builder.Factory factory = ResourceXMLElement.Builder.Factory.newInstance(this);
        ResourceXMLElement.Builder builder = factory.createBuilder(IOSubsystemResourceDescription.INSTANCE);
        if (!this.since(VERSION_4_0)) {
            builder.excludeAttribute(IOSubsystemResourceDescription.DEFAULT_WORKER);
            builder.withOperationTransformation(new UnaryOperator<>() {
                @Override
                public ModelNode apply(ModelNode operation) {
                    // Apply "magic" default worker referenced by other subsystems
                    operation.get(IOSubsystemResourceDescription.DEFAULT_WORKER.getName()).set(IOSubsystemResourceRegistrar.LEGACY_DEFAULT_WORKER);
                    return operation;
                }
            });
        }
        ResourceXMLElement.Builder workerBuilder = factory.createBuilder(ResourceDescription.of(WorkerResourceDefinition.PATH, List.of(WorkerResourceDefinition.ATTRIBUTES)));
        if (!this.since(VERSION_3_0)) {
            workerBuilder.excludeAttribute(WorkerResourceDefinition.WORKER_TASK_CORE_THREADS);
        }
        if (this.since(VERSION_2_0)) {
            workerBuilder.appendChild(factory.createBuilder(ResourceDescription.of(OutboundBindAddressResourceDefinition.PATH, OutboundBindAddressResourceDefinition.ATTRIBUTES)).build());
        }
        return builder.appendChild(workerBuilder.build())
                .appendChild(factory.createBuilder(ResourceDescription.of(BufferPoolResourceDefinition.PATH, BufferPoolResourceDefinition.ATTRIBUTES)).build())
                .build();
    }
}
