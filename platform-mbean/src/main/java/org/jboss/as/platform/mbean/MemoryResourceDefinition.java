/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import static org.jboss.as.platform.mbean.PlatformMBeanConstants.MEMORY;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.MEMORY_PATH;

import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2013 Red Hat Inc.
 */
class MemoryResourceDefinition extends SimpleResourceDefinition {

    //metrics
    private static SimpleAttributeDefinition OBJECT_PENDING_FINALIZATION_COUNT = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.OBJECT_PENDING_FINALIZATION_COUNT, ModelType.INT, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.NONE)
            .build();


    private static SimpleAttributeDefinition HEAP_MEMORY_USAGE = new ObjectTypeAttributeDefinition.Builder(
            PlatformMBeanConstants.HEAP_MEMORY_USAGE,
            PlatformMBeanConstants.MEMORY_INIT,
            PlatformMBeanConstants.MEMORY_USED,
            PlatformMBeanConstants.MEMORY_COMMITTED,
            PlatformMBeanConstants.MEMORY_MAX
    )
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static SimpleAttributeDefinition NON_HEAP_MEMORY_USAGE = new ObjectTypeAttributeDefinition.Builder(
            PlatformMBeanConstants.NON_HEAP_MEMORY_USAGE,
            PlatformMBeanConstants.MEMORY_INIT,
            PlatformMBeanConstants.MEMORY_USED,
            PlatformMBeanConstants.MEMORY_COMMITTED,
            PlatformMBeanConstants.MEMORY_MAX)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static AttributeDefinition VERBOSE = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.VERBOSE, ModelType.BOOLEAN, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    private static final List<SimpleAttributeDefinition> METRICS = Arrays.asList(
            OBJECT_PENDING_FINALIZATION_COUNT,
            HEAP_MEMORY_USAGE,
            NON_HEAP_MEMORY_USAGE
    );
    private static final List<AttributeDefinition> READ_WRITE_ATTRIBUTES = Arrays.asList(
            VERBOSE
    );

    public static final List<String> MEMORY_METRICS = Arrays.asList(
            PlatformMBeanConstants.OBJECT_PENDING_FINALIZATION_COUNT,
            PlatformMBeanConstants.HEAP_MEMORY_USAGE,
            PlatformMBeanConstants.NON_HEAP_MEMORY_USAGE
    );
    public static final List<String> MEMORY_READ_WRITE_ATTRIBUTES = Arrays.asList(
            VERBOSE.getName()
    );

    static final MemoryResourceDefinition INSTANCE = new MemoryResourceDefinition();


    private MemoryResourceDefinition() {
        super(new Parameters(MEMORY_PATH, PlatformMBeanUtil.getResolver(MEMORY)).setRuntime());
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        super.registerAttributes(registration);
        registration.registerReadOnlyAttribute(PlatformMBeanConstants.OBJECT_NAME, MemoryMXBeanAttributeHandler.INSTANCE);

        for (AttributeDefinition attribute : READ_WRITE_ATTRIBUTES) {
            registration.registerReadWriteAttribute(attribute, MemoryMXBeanAttributeHandler.INSTANCE, MemoryMXBeanAttributeHandler.INSTANCE);
        }

        for (SimpleAttributeDefinition attribute : METRICS) {
            registration.registerMetric(attribute, MemoryMXBeanAttributeHandler.INSTANCE);
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(MemoryMXBeanGCHandler.DEFINITION, MemoryMXBeanGCHandler.INSTANCE);
    }
}

