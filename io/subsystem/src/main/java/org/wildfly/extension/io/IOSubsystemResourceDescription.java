/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import java.util.stream.Stream;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.SubsystemResourceDescription;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.wildfly.io.IOServiceDescriptor;
import org.wildfly.subsystem.resource.capability.CapabilityReference;
import org.wildfly.subsystem.resource.capability.CapabilityReferenceAttributeDefinition;
import org.xnio.XnioWorker;

/**
 * Describes the IO subsystem resource.
 */
public enum IOSubsystemResourceDescription implements SubsystemResourceDescription {
    INSTANCE;

    static final RuntimeCapability<Void> DEFAULT_WORKER_CAPABILITY = RuntimeCapability.Builder.of(IOServiceDescriptor.DEFAULT_WORKER).build();

    static final CapabilityReferenceAttributeDefinition<XnioWorker> DEFAULT_WORKER = new CapabilityReferenceAttributeDefinition.Builder<>("default-worker", CapabilityReference.builder(DEFAULT_WORKER_CAPABILITY, IOServiceDescriptor.NAMED_WORKER).build())
            .setRequired(false)
            .build();

    @Override
    public String getName() {
        return "io";
    }

    @Override
    public Stream<AttributeDefinition> getAttributes() {
        return Stream.of(DEFAULT_WORKER);
    }
}
