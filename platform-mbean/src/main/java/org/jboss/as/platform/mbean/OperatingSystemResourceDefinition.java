/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import static org.jboss.as.platform.mbean.PlatformMBeanConstants.OPERATING_SYSTEM_PATH;

import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.operations.global.ReadResourceHandler;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2013 Red Hat Inc.
 */
class OperatingSystemResourceDefinition extends SimpleResourceDefinition {


    private static SimpleAttributeDefinition AVAILABLE_PROCESSORS = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.AVAILABLE_PROCESSORS, ModelType.INT, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.NONE)
            .build();

    private static SimpleAttributeDefinition SYSTEM_LOAD_AVERAGE = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.SYSTEM_LOAD_AVERAGE, ModelType.DOUBLE, false)
            .setMeasurementUnit(MeasurementUnit.PERCENTAGE)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();


    private static AttributeDefinition ARCH = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.ARCH, ModelType.STRING, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    private static AttributeDefinition VERSION = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.VERSION, ModelType.STRING, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    private static SimpleAttributeDefinition COMMITTED_VIRTUAL_MEMORY_SIZE = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.COMMITTED_VIRTUAL_MEMORY_SIZE, ModelType.LONG, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.BYTES)
            .setRequired(false)
            .setStability(Stability.COMMUNITY)
            .build();
    private static SimpleAttributeDefinition FREE_MEMORY_SIZE = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.FREE_MEMORY_SIZE, ModelType.LONG, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.BYTES)
            .setRequired(false)
            .setStability(Stability.COMMUNITY)
            .build();
    private static SimpleAttributeDefinition FREE_SWAP_SPACE_SIZE = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.FREE_SWAP_SPACE_SIZE, ModelType.LONG, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.BYTES)
            .setRequired(false)
            .setStability(Stability.COMMUNITY)
            .build();
    private static SimpleAttributeDefinition MAX_FILE_DESCRIPTOR_COUNT = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.MAX_FILE_DESCRIPTOR_COUNT, ModelType.LONG, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.NONE)
            .setRequired(false)
            .setStability(Stability.COMMUNITY)
            .build();
    private static SimpleAttributeDefinition OPEN_FILE_DESCRIPTOR_COUNT = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.OPEN_FILE_DESCRIPTOR_COUNT, ModelType.LONG, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.NONE)
            .setRequired(false)
            .setStability(Stability.COMMUNITY)
            .build();
    private static SimpleAttributeDefinition PROCESS_CPU_LOAD = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.PROCESS_CPU_LOAD, ModelType.DOUBLE, true)
            .setMeasurementUnit(MeasurementUnit.PERCENTAGE)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(false)
            .setStability(Stability.COMMUNITY)
            .build();
    private static SimpleAttributeDefinition PROCESS_CPU_TIME = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.PROCESS_CPU_TIME, ModelType.LONG, true)
            .setMeasurementUnit(MeasurementUnit.NANOSECONDS)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(false)
            .setStability(Stability.COMMUNITY)
            .build();
    private static SimpleAttributeDefinition CPU_LOAD = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.CPU_LOAD, ModelType.DOUBLE, true)
            .setMeasurementUnit(MeasurementUnit.PERCENTAGE)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(false)
            .setStability(Stability.COMMUNITY)
            .build();
    private static SimpleAttributeDefinition TOTAL_MEMORY_SIZE = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.TOTAL_MEMORY_SIZE, ModelType.LONG, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.BYTES)
            .setRequired(false)
            .setStability(Stability.COMMUNITY)
            .build();
    private static SimpleAttributeDefinition TOTAL_SWAP_SPACE_SIZE = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.TOTAL_SWAP_SPACE_SIZE, ModelType.LONG, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.BYTES)
            .setRequired(false)
            .setStability(Stability.COMMUNITY)
            .build();

    private static final List<SimpleAttributeDefinition> METRICS = Arrays.asList(
            AVAILABLE_PROCESSORS,
            COMMITTED_VIRTUAL_MEMORY_SIZE,
            FREE_MEMORY_SIZE,
            FREE_SWAP_SPACE_SIZE,
            MAX_FILE_DESCRIPTOR_COUNT,
            OPEN_FILE_DESCRIPTOR_COUNT,
            PROCESS_CPU_LOAD,
            PROCESS_CPU_TIME,
            SYSTEM_LOAD_AVERAGE,
            CPU_LOAD,
            TOTAL_MEMORY_SIZE,
            TOTAL_SWAP_SPACE_SIZE
    );
    private static final List<AttributeDefinition> READ_ATTRIBUTES = Arrays.asList(
            PlatformMBeanConstants.NAME,
            ARCH,
            VERSION
    );

    public static final List<String> OPERATING_SYSTEM_READ_ATTRIBUTES = Arrays.asList(
            PlatformMBeanConstants.NAME.getName(),
            ARCH.getName(),
            VERSION.getName()
    );

    public static final List<String> OPERATING_SYSTEM_EXTENDED_METRICS = Arrays.asList(
            COMMITTED_VIRTUAL_MEMORY_SIZE.getName(),
            FREE_MEMORY_SIZE.getName(),
            FREE_SWAP_SPACE_SIZE.getName(),
            PROCESS_CPU_LOAD.getName(),
            PROCESS_CPU_TIME.getName(),
            CPU_LOAD.getName(),
            TOTAL_MEMORY_SIZE.getName(),
            TOTAL_SWAP_SPACE_SIZE.getName(),
            // Unix specific
            MAX_FILE_DESCRIPTOR_COUNT.getName(),
            OPEN_FILE_DESCRIPTOR_COUNT.getName()
    );

    public static final List<String> OPERATING_SYSTEM_METRICS = Arrays.asList(
            AVAILABLE_PROCESSORS.getName(),
            SYSTEM_LOAD_AVERAGE.getName()
    );


    static final OperatingSystemResourceDefinition INSTANCE = new OperatingSystemResourceDefinition();

    private OperatingSystemResourceDefinition() {
        super(new Parameters(OPERATING_SYSTEM_PATH,
                PlatformMBeanUtil.getResolver(PlatformMBeanConstants.OPERATING_SYSTEM)).setRuntime());
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        super.registerAttributes(registration);
        registration.registerReadOnlyAttribute(PlatformMBeanConstants.OBJECT_NAME, OperatingSystemMXBeanAttributeHandler.INSTANCE);

        for (AttributeDefinition attribute : READ_ATTRIBUTES) {
            registration.registerReadOnlyAttribute(attribute, OperatingSystemMXBeanAttributeHandler.INSTANCE);
        }

        for (SimpleAttributeDefinition attribute : METRICS) {
            registration.registerMetric(attribute, OperatingSystemMXBeanAttributeHandler.INSTANCE);
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(ReadResourceHandler.DEFINITION, OperatingSystemMXBeanReadResourceHandler.INSTANCE);
    }
}

