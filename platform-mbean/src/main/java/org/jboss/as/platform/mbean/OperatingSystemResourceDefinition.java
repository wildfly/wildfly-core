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

    private static final List<SimpleAttributeDefinition> METRICS = Arrays.asList(
            AVAILABLE_PROCESSORS,
            SYSTEM_LOAD_AVERAGE
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

