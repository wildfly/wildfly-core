/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.platform.mbean;


import java.util.Arrays;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PrimitiveListAttributeDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.operations.global.ReadResourceHandler;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import static org.jboss.as.platform.mbean.PlatformMBeanConstants.PLATFORM_LOGGING_PATH;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelType;

class PlatformLoggingResourceDefinition extends SimpleResourceDefinition {

    static AttributeDefinition LOGGER_NAMES = new PrimitiveListAttributeDefinition.Builder(PlatformMBeanConstants.LOGGER_NAMES, ModelType.STRING)
            .setStorageRuntime()
            .setRuntimeServiceNotRequired()
            .setStability(Stability.COMMUNITY)
            .build();

    static final List<AttributeDefinition> READ_ATTRIBUTES = Arrays.asList(
            LOGGER_NAMES
    );

    static final List<String> PLATFORM_LOGGING_READ_ATTRIBUTES = Arrays.asList(
            LOGGER_NAMES.getName()
    );

    static final PlatformLoggingResourceDefinition INSTANCE = new PlatformLoggingResourceDefinition();

    private PlatformLoggingResourceDefinition() {
        super(new Parameters(PLATFORM_LOGGING_PATH,
                PlatformMBeanUtil.getResolver(PlatformMBeanConstants.PLATFORM_LOGGING)).setRuntime());
    }

    @Override
    public Stability getStability() {
        return Stability.COMMUNITY;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        super.registerAttributes(registration);
        registration.registerReadOnlyAttribute(PlatformMBeanConstants.OBJECT_NAME, PlatformLoggingMXBeanAttributeHandler.INSTANCE);

        for (AttributeDefinition attribute : READ_ATTRIBUTES) {
            registration.registerReadOnlyAttribute(attribute, PlatformLoggingMXBeanAttributeHandler.INSTANCE);
        }

    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
        resourceRegistration.registerOperationHandler(ReadResourceHandler.DEFINITION, PlatformLoggingMXBeanReadResourceHandler.INSTANCE);
        // Do not register any operation. The logging must be configured using the Logging subsystem.
    }
}

