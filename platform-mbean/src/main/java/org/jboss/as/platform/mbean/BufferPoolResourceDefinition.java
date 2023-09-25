/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;


import static org.jboss.as.platform.mbean.PlatformMBeanConstants.BUFFER_POOL;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.NAME;

import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2013 Red Hat Inc.
 */
class BufferPoolResourceDefinition extends SimpleResourceDefinition {


    private static AttributeDefinition MEMORY_USED_NAME = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.MEMORY_USED_NAME, ModelType.LONG, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.BYTES)
            .build();
    private static AttributeDefinition TOTAL_CAPACITY = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.TOTAL_CAPACITY, ModelType.LONG, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setMeasurementUnit(MeasurementUnit.BYTES)
            .build();

    private static AttributeDefinition COUNT = SimpleAttributeDefinitionBuilder.create(PlatformMBeanConstants.COUNT, ModelType.LONG, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();

    private static final List<AttributeDefinition> METRICS = Arrays.asList(
            COUNT,
            MEMORY_USED_NAME,
            TOTAL_CAPACITY
    );

    private static final List<AttributeDefinition> READ_ATTRIBUTES = Arrays.asList(
            PlatformMBeanConstants.NAME
    );

    static final List<String> BUFFER_POOL_METRICS = Arrays.asList(
            COUNT.getName(),
            MEMORY_USED_NAME.getName(),
            TOTAL_CAPACITY.getName()
    );


    static final BufferPoolResourceDefinition INSTANCE = new BufferPoolResourceDefinition();


    private BufferPoolResourceDefinition() {
        super(new Parameters(PathElement.pathElement(NAME.getName()),
                PlatformMBeanUtil.getResolver(BUFFER_POOL)).setRuntime());
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        super.registerAttributes(registration);
        registration.registerReadOnlyAttribute(PlatformMBeanConstants.OBJECT_NAME, BufferPoolMXBeanAttributeHandler.INSTANCE);
        for (AttributeDefinition attribute : READ_ATTRIBUTES) {
            registration.registerReadOnlyAttribute(attribute, BufferPoolMXBeanAttributeHandler.INSTANCE);
        }

        for (AttributeDefinition attribute : METRICS) {
            registration.registerMetric(attribute, BufferPoolMXBeanAttributeHandler.INSTANCE);
        }
    }

}

