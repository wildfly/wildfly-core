/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import static org.jboss.as.controller.registry.AttributeAccess.Flag.COUNTER_METRIC;
import static org.jboss.as.controller.registry.AttributeAccess.Flag.GAUGE_METRIC;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.NAME;

import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ObjectMapAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.DURATION;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.END_TIME;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.ID;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.MEMORY_COMMITTED;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.MEMORY_INIT;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.MEMORY_MAX;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.MEMORY_USED;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.START_TIME;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2013 Red Hat Inc.
 */
class GarbageCollectorResourceDefinition extends SimpleResourceDefinition {
    //metrics
    private static SimpleAttributeDefinition COLLECTION_COUNT = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.COLLECTION_COUNT, ModelType.LONG, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.NONE)
            .setFlags(COUNTER_METRIC)
            .build();
    private static SimpleAttributeDefinition COLLECTION_TIME = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.COLLECTION_TIME, ModelType.LONG, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.MILLISECONDS)
            .setFlags(GAUGE_METRIC)
            .build();
    private static AttributeDefinition MEMORY_POOL_NAMES = new StringListAttributeDefinition.Builder(PlatformMBeanConstants.MEMORY_POOL_NAMES)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    private static SimpleAttributeDefinition DURATION_ATTRIBUTE = SimpleAttributeDefinitionBuilder.create(DURATION, ModelType.LONG, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.MILLISECONDS)
            .setStability(Stability.COMMUNITY)
            .build();
    private static SimpleAttributeDefinition END_TIME_ATTRIBUTE = SimpleAttributeDefinitionBuilder.create(END_TIME, ModelType.LONG, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.MILLISECONDS)
            .setStability(Stability.COMMUNITY)
            .build();
    private static SimpleAttributeDefinition ID_ATTRIBUTE = SimpleAttributeDefinitionBuilder.create(ID, ModelType.LONG, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.NONE)
            .setStability(Stability.COMMUNITY)
            .build();
    private static SimpleAttributeDefinition START_TIME_ATTRIBUTE = SimpleAttributeDefinitionBuilder.create(START_TIME, ModelType.LONG, true)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.MILLISECONDS)
            .setStability(Stability.COMMUNITY)
            .build();
    private static final AttributeDefinition MEMORY_USAGE_AFTER_GC_ATTRIBUTE = ObjectMapAttributeDefinition.Builder.of(
            PlatformMBeanConstants.MEMORY_USAGE_AFTER_GC,
            ObjectTypeAttributeDefinition.Builder.of(PlatformMBeanConstants.MEMORY_USAGE_AFTER_GC, MEMORY_INIT, MEMORY_USED, MEMORY_COMMITTED, MEMORY_MAX)
                    .build())
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setStability(Stability.COMMUNITY)
            .build();
    private static final AttributeDefinition MEMORY_USAGE_BEFORE_GC_ATTRIBUTE = ObjectMapAttributeDefinition.Builder.of(
            PlatformMBeanConstants.MEMORY_USAGE_BEFORE_GC,
            ObjectTypeAttributeDefinition.Builder.of(PlatformMBeanConstants.MEMORY_USAGE_BEFORE_GC, MEMORY_INIT, MEMORY_USED, MEMORY_COMMITTED, MEMORY_MAX)
                    .build())
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setStability(Stability.COMMUNITY)
            .build();
    private static SimpleAttributeDefinition LAST_GC_INFO = new ObjectTypeAttributeDefinition.Builder(
            PlatformMBeanConstants.LAST_GC_INFO,
            DURATION_ATTRIBUTE,
            END_TIME_ATTRIBUTE,
            ID_ATTRIBUTE,
            START_TIME_ATTRIBUTE,
            MEMORY_USAGE_AFTER_GC_ATTRIBUTE,
            MEMORY_USAGE_BEFORE_GC_ATTRIBUTE
    )
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setRequired(false)
            .setStability(Stability.COMMUNITY)
            .build();

    private static final List<SimpleAttributeDefinition> METRICS = Arrays.asList(
            COLLECTION_COUNT,
            COLLECTION_TIME
    );
    private static final List<AttributeDefinition> READ_ATTRIBUTES = Arrays.asList(
            NAME,
            PlatformMBeanConstants.VALID,
            MEMORY_POOL_NAMES,
            LAST_GC_INFO
    );


    static final List<String> GARBAGE_COLLECTOR_READ_ATTRIBUTES = Arrays.asList(
            NAME.getName(),
            PlatformMBeanConstants.VALID.getName(),
            MEMORY_POOL_NAMES.getName()
    );
    static final List<String> GARBAGE_COLLECTOR_EXTENDED_READ_ATTRIBUTES = Arrays.asList(
            LAST_GC_INFO.getName()
    );
    static final List<String> GARBAGE_COLLECTOR_METRICS = Arrays.asList(
            COLLECTION_COUNT.getName(),
            COLLECTION_TIME.getName()
    );
    static final GarbageCollectorResourceDefinition INSTANCE = new GarbageCollectorResourceDefinition();

    private GarbageCollectorResourceDefinition() {
        super(new Parameters(PathElement.pathElement(NAME.getName()),
                PlatformMBeanUtil.getResolver(PlatformMBeanConstants.GARBAGE_COLLECTOR)).setRuntime());
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        super.registerAttributes(registration);
        registration.registerReadOnlyAttribute(PlatformMBeanConstants.OBJECT_NAME, GarbageCollectorMXBeanAttributeHandler.INSTANCE);

        for (AttributeDefinition attribute : READ_ATTRIBUTES) {
            registration.registerReadOnlyAttribute(attribute, GarbageCollectorMXBeanAttributeHandler.INSTANCE);
        }

        for (SimpleAttributeDefinition attribute : METRICS) {
            registration.registerMetric(attribute, GarbageCollectorMXBeanAttributeHandler.INSTANCE);
        }
    }


}

