/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;


import static org.jboss.as.controller.registry.AttributeAccess.Flag.COUNTER_METRIC;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.NAME;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.VALID;

import java.lang.management.MemoryType;
import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.operations.global.ReadResourceHandler;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2013 Red Hat Inc.
 */
class MemoryPoolResourceDefinition extends SimpleResourceDefinition {


    private static AttributeDefinition MEMORY_MANAGER_NAMES = new StringListAttributeDefinition.Builder(PlatformMBeanConstants.MEMORY_MANAGER_NAMES)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    private static AttributeDefinition TYPE = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.TYPE, ModelType.STRING, false)
            .setValidator(new EnumValidator<MemoryType>(MemoryType.class))
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    private static AttributeDefinition USAGE_THRESHOLD = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.USAGE_THRESHOLD, ModelType.LONG, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.BYTES)
            .setValidator(new IntRangeValidator(0))
            .build();

    private static AttributeDefinition USAGE_THRESHOLD_EXCEEDED = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.USAGE_THRESHOLD_EXCEEDED, ModelType.BOOLEAN, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    private static AttributeDefinition USAGE_THRESHOLD_SUPPORTED = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.USAGE_THRESHOLD_SUPPORTED, ModelType.BOOLEAN, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    private static AttributeDefinition USAGE_THRESHOLD_COUNT = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.USAGE_THRESHOLD_COUNT, ModelType.LONG, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.NONE)
            .setFlags(COUNTER_METRIC)
            .build();

    private static AttributeDefinition COLLECTION_USAGE_THRESHOLD_COUNT = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.COLLECTION_USAGE_THRESHOLD_COUNT, ModelType.LONG, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.NONE)
            .setFlags(COUNTER_METRIC)
            .build();

    private static AttributeDefinition COLLECTION_USAGE_THRESHOLD = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.COLLECTION_USAGE_THRESHOLD, ModelType.LONG, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.BYTES)
            .setValidator(new IntRangeValidator(0))
            .build();

    private static AttributeDefinition COLLECTION_USAGE_THRESHOLD_SUPPORTED = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.COLLECTION_USAGE_THRESHOLD_SUPPORTED, ModelType.BOOLEAN, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    private static AttributeDefinition COLLECTION_USAGE_THRESHOLD_EXCEEDED = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.COLLECTION_USAGE_THRESHOLD_EXCEEDED, ModelType.BOOLEAN, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();


    private static AttributeDefinition USAGE = new ObjectTypeAttributeDefinition.Builder(
            PlatformMBeanConstants.USAGE,
            PlatformMBeanConstants.MEMORY_INIT,
            PlatformMBeanConstants.MEMORY_USED,
            PlatformMBeanConstants.MEMORY_COMMITTED,
            PlatformMBeanConstants.MEMORY_MAX
    )
            .setStorageRuntime()
            .setRequired(true)
            .build();

    private static AttributeDefinition PEAK_USAGE = new ObjectTypeAttributeDefinition.Builder(
            PlatformMBeanConstants.PEAK_USAGE,
            PlatformMBeanConstants.MEMORY_INIT,
            PlatformMBeanConstants.MEMORY_USED,
            PlatformMBeanConstants.MEMORY_COMMITTED,
            PlatformMBeanConstants.MEMORY_MAX)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(true)
            .build();

    private static AttributeDefinition COLLECTION_USAGE = new ObjectTypeAttributeDefinition.Builder(
            PlatformMBeanConstants.COLLECTION_USAGE,
            PlatformMBeanConstants.MEMORY_INIT,
            PlatformMBeanConstants.MEMORY_USED,
            PlatformMBeanConstants.MEMORY_COMMITTED,
            PlatformMBeanConstants.MEMORY_MAX)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();


    private static final List<AttributeDefinition> METRICS = Arrays.asList(
            USAGE,
            PEAK_USAGE,
            USAGE_THRESHOLD_EXCEEDED,
            USAGE_THRESHOLD_COUNT,
            COLLECTION_USAGE_THRESHOLD_EXCEEDED,
            COLLECTION_USAGE_THRESHOLD_COUNT,
            COLLECTION_USAGE
    );
    private static final List<AttributeDefinition> READ_WRITE_ATTRIBUTES = Arrays.asList(
            USAGE_THRESHOLD,
            COLLECTION_USAGE_THRESHOLD
    );
    private static final List<AttributeDefinition> READ_ATTRIBUTES = Arrays.asList(
            NAME,
            TYPE,
            VALID,
            MEMORY_MANAGER_NAMES,
            USAGE_THRESHOLD_SUPPORTED,
            COLLECTION_USAGE_THRESHOLD_SUPPORTED
    );


    static final List<String> MEMORY_POOL_METRICS = Arrays.asList(
            USAGE.getName(),
            PEAK_USAGE.getName(),
            USAGE_THRESHOLD_EXCEEDED.getName(),
            USAGE_THRESHOLD_COUNT.getName(),
            COLLECTION_USAGE_THRESHOLD_EXCEEDED.getName(),
            COLLECTION_USAGE_THRESHOLD_COUNT.getName(),
            COLLECTION_USAGE.getName()
    );
    static final List<String> MEMORY_POOL_READ_WRITE_ATTRIBUTES = Arrays.asList(
            USAGE_THRESHOLD.getName(),
            COLLECTION_USAGE_THRESHOLD.getName()
    );

    static final List<String> MEMORY_POOL_READ_ATTRIBUTES = Arrays.asList(
            NAME.getName(),
            TYPE.getName(),
            PlatformMBeanConstants.VALID.getName(),
            MEMORY_MANAGER_NAMES.getName(),
            USAGE_THRESHOLD_SUPPORTED.getName(),
            COLLECTION_USAGE_THRESHOLD_SUPPORTED.getName()
    );


    static final MemoryPoolResourceDefinition INSTANCE = new MemoryPoolResourceDefinition();


    private MemoryPoolResourceDefinition() {
        super(new Parameters(PathElement.pathElement(NAME.getName()),
                PlatformMBeanUtil.getResolver(PlatformMBeanConstants.MEMORY_POOL)).setRuntime());
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        super.registerAttributes(registration);
        registration.registerReadOnlyAttribute(PlatformMBeanConstants.OBJECT_NAME, MemoryMXBeanAttributeHandler.INSTANCE);

        for (AttributeDefinition attribute : READ_WRITE_ATTRIBUTES) {
            registration.registerReadWriteAttribute(attribute, MemoryPoolMXBeanAttributeHandler.INSTANCE, MemoryPoolMXBeanAttributeHandler.INSTANCE);
        }
        for (AttributeDefinition attribute : READ_ATTRIBUTES) {
            registration.registerReadOnlyAttribute(attribute, MemoryPoolMXBeanAttributeHandler.INSTANCE);
        }

        for (AttributeDefinition attribute : METRICS) {
            registration.registerMetric(attribute, MemoryPoolMXBeanAttributeHandler.INSTANCE);
        }
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(ReadResourceHandler.DEFINITION, MemoryPoolMXBeanReadResourceHandler.INSTANCE);
        resourceRegistration.registerOperationHandler(MemoryPoolMXBeanResetPeakUsageHandler.DEFINITION, MemoryPoolMXBeanResetPeakUsageHandler.INSTANCE);
    }
}

