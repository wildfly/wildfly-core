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
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
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

    private static final List<SimpleAttributeDefinition> METRICS = Arrays.asList(
            COLLECTION_COUNT,
            COLLECTION_TIME
    );
    private static final List<AttributeDefinition> READ_ATTRIBUTES = Arrays.asList(
            NAME,
            PlatformMBeanConstants.VALID,
            MEMORY_POOL_NAMES
    );


    static final List<String> GARBAGE_COLLECTOR_READ_ATTRIBUTES = Arrays.asList(
            NAME.getName(),
            PlatformMBeanConstants.VALID.getName(),
            MEMORY_POOL_NAMES.getName()
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

