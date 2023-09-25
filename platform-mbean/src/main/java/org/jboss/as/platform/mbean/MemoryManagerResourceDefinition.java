/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;

import static org.jboss.as.platform.mbean.PlatformMBeanConstants.NAME;

import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

/**
 * @author Tomaz Cerar (c) 2013 Red Hat Inc.
 */
class MemoryManagerResourceDefinition extends SimpleResourceDefinition {
    private static SimpleAttributeDefinition VALID = SimpleAttributeDefinitionBuilder.create(ModelDescriptionConstants.VALID, ModelType.BOOLEAN, false)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();
    private static AttributeDefinition MEMORY_POOL_NAMES = new StringListAttributeDefinition.Builder(PlatformMBeanConstants.MEMORY_POOL_NAMES)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .build();


    private static final List<AttributeDefinition> METRICS = Arrays.asList(
            NAME, // TODO is name really a metric? It seems to be a runtime r/o attibute
            VALID,
            MEMORY_POOL_NAMES
    );

    static final List<String> MEMORY_MANAGER_READ_ATTRIBUTES = Arrays.asList(
            ModelDescriptionConstants.NAME, VALID.getName(), MEMORY_POOL_NAMES.getName()
    );


    static final MemoryManagerResourceDefinition INSTANCE = new MemoryManagerResourceDefinition();

    private MemoryManagerResourceDefinition() {
        super(new Parameters(PathElement.pathElement(NAME.getName()),
                PlatformMBeanUtil.getResolver(PlatformMBeanConstants.MEMORY_MANAGER)).setRuntime());
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        super.registerAttributes(registration);
        registration.registerReadOnlyAttribute(PlatformMBeanConstants.OBJECT_NAME, MemoryManagerMXBeanAttributeHandler.INSTANCE);

        for (AttributeDefinition attribute : METRICS) {
            registration.registerMetric(attribute, MemoryManagerMXBeanAttributeHandler.INSTANCE);
        }
    }


}

