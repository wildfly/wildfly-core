/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import java.util.List;
import java.util.Map;

import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.persistence.xml.ResourceXMLChoice;
import org.jboss.as.controller.persistence.xml.ResourceXMLParticleFactory;
import org.jboss.as.controller.persistence.xml.SubsystemResourceRegistrationXMLElement;
import org.jboss.as.controller.persistence.xml.SubsystemResourceXMLSchema;
import org.jboss.as.controller.persistence.xml.NamedResourceRegistrationXMLElement;
import org.jboss.as.controller.persistence.xml.ResourceRegistrationXMLElement;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.as.controller.xml.XMLCardinality;
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
        this.namespace = SubsystemSchema.createLegacySubsystemURN(IOSubsystemResourceDefinitionRegistrar.REGISTRATION.getName(), new IntVersion(major, minor));
    }

    @Override
    public VersionedNamespace<IntVersion, IOSubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public SubsystemResourceRegistrationXMLElement getSubsystemXMLElement() {
        SubsystemResourceRegistrationXMLElement.Builder builder = this.factory.subsystemElement(IOSubsystemResourceDefinitionRegistrar.REGISTRATION);
        if (this.since(VERSION_4_0)) {
            builder.addAttribute(IOSubsystemResourceDefinitionRegistrar.DEFAULT_WORKER);
        } else {
            // Apply "magic" default worker referenced by other subsystems
            builder.withDefaultValues(Map.of(IOSubsystemResourceDefinitionRegistrar.DEFAULT_WORKER, IOSubsystemResourceDefinitionRegistrar.LEGACY_DEFAULT_WORKER));
        }
        ResourceXMLChoice content = this.factory.choice().withCardinality(XMLCardinality.Unbounded.REQUIRED)
                .addElement(this.workerElement())
                .addElement(this.bufferPoolElement())
                .build();
        return builder.withContent(content).build();
    }

    private ResourceRegistrationXMLElement workerElement() {
        NamedResourceRegistrationXMLElement.Builder builder = this.factory.namedElement(ResourceRegistration.of(WorkerResourceDefinition.PATH))
                .addAttributes(List.of(WorkerResourceDefinition.WORKER_IO_THREADS, WorkerResourceDefinition.WORKER_TASK_KEEPALIVE, WorkerResourceDefinition.WORKER_TASK_MAX_THREADS, WorkerResourceDefinition.STACK_SIZE));
        if (this.since(VERSION_3_0)) {
            builder.addAttribute(WorkerResourceDefinition.WORKER_TASK_CORE_THREADS);
        }
        if (this.since(VERSION_2_0)) {
            builder.withContent(this.factory.choice().withCardinality(XMLCardinality.Unbounded.OPTIONAL).addElement(this.outboundBindAddressElement()).build());
        }
        return builder.build();
    }

    private ResourceRegistrationXMLElement outboundBindAddressElement() {
        return this.factory.namedElement(ResourceRegistration.of(OutboundBindAddressResourceDefinition.PATH))
                .addAttributes(OutboundBindAddressResourceDefinition.ATTRIBUTES)
                .build();
    }

    private ResourceRegistrationXMLElement bufferPoolElement() {
        return this.factory.namedElement(ResourceRegistration.of(BufferPoolResourceDefinition.PATH))
                .addAttributes(BufferPoolResourceDefinition.ATTRIBUTES)
                .build();
    }
}
