/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import java.util.List;
import java.util.function.UnaryOperator;

import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.persistence.xml.ResourceModelXMLChoice;
import org.jboss.as.controller.persistence.xml.ResourceXMLElement;
import org.jboss.as.controller.persistence.xml.ResourceXMLParticleFactory;
import org.jboss.as.controller.persistence.xml.SubsystemResourceXMLSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.as.controller.xml.XMLChoice;
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

    private final ResourceXMLParticleFactory factory = ResourceXMLParticleFactory.newInstance(this);
    private final VersionedNamespace<IntVersion, IOSubsystemSchema> namespace;

    IOSubsystemSchema(int major, int minor) {
        this.namespace = SubsystemSchema.createLegacySubsystemURN(IOSubsystemResourceRegistrar.INSTANCE.getName(), new IntVersion(major, minor));
    }

    @Override
    public VersionedNamespace<IntVersion, IOSubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public ResourceXMLElement getSubsystemResourceXMLElement() {
        ResourceXMLElement.Builder builder = this.factory.element(IOSubsystemResourceRegistrar.INSTANCE);
        if (this.since(VERSION_4_0)) {
            builder.addAttribute(IOSubsystemResourceRegistrar.DEFAULT_WORKER);
        } else {
            builder.withOperationTransformation(new UnaryOperator<>() {
                @Override
                public ModelNode apply(ModelNode operation) {
                    // Apply "magic" default worker referenced by other subsystems
                    operation.get(IOSubsystemResourceRegistrar.DEFAULT_WORKER.getName()).set(IOSubsystemResourceRegistrar.LEGACY_DEFAULT_WORKER);
                    return operation;
                }
            });
        }
        ResourceXMLElement.Builder workerBuilder = this.factory.element(ResourceRegistration.of(WorkerResourceDefinition.PATH)).addAttributes(List.of(
                WorkerResourceDefinition.WORKER_IO_THREADS,
                WorkerResourceDefinition.WORKER_TASK_KEEPALIVE,
                WorkerResourceDefinition.WORKER_TASK_MAX_THREADS,
                WorkerResourceDefinition.STACK_SIZE));
        if (this.since(VERSION_3_0)) {
            workerBuilder.addAttribute(WorkerResourceDefinition.WORKER_TASK_CORE_THREADS);
        }
        if (this.since(VERSION_2_0)) {
            workerBuilder.withContent(XMLChoice.singleton(this.factory.element(ResourceRegistration.of(OutboundBindAddressResourceDefinition.PATH)).addAttributes(OutboundBindAddressResourceDefinition.ATTRIBUTES).build()));
        }
        ResourceModelXMLChoice content = this.factory.choice().withCardinality(XMLCardinality.Unbounded.REQUIRED)
                .addElement(workerBuilder.build())
                .addElement(this.factory.element(ResourceRegistration.of(BufferPoolResourceDefinition.PATH)).addAttributes(BufferPoolResourceDefinition.ATTRIBUTES).build())
                .build();
        return builder.withContent(content).build();
    }
}
