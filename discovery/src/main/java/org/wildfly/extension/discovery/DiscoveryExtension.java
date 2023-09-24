/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import java.util.EnumSet;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.PersistentResourceXMLDescriptionWriter;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ParentResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.SubsystemResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.wildfly.discovery.spi.DiscoveryProvider;

/**
 * The extension class for the WildFly Discovery extension.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class DiscoveryExtension implements Extension {

    static final String SUBSYSTEM_NAME = "discovery";

    static final ParentResourceDescriptionResolver SUBSYSTEM_RESOLVER = new SubsystemResourceDescriptionResolver(SUBSYSTEM_NAME, DiscoveryExtension.class);

    static final RuntimeCapability<?> DISCOVERY_PROVIDER_CAPABILITY = RuntimeCapability.Builder.of("org.wildfly.discovery.provider", true)
            .setServiceType(DiscoveryProvider.class)
            .setAllowMultipleRegistrations(true)
            .build();

    @Override
    public void initialize(final ExtensionContext context) {
        final SubsystemRegistration subsystemRegistration = context.registerSubsystem(SUBSYSTEM_NAME, DiscoverySubsystemModel.CURRENT.getVersion());
        subsystemRegistration.setHostCapable();
        subsystemRegistration.registerXMLElementWriter(new PersistentResourceXMLDescriptionWriter(DiscoverySubsystemSchema.CURRENT));

        final ManagementResourceRegistration resourceRegistration = subsystemRegistration.registerSubsystemModel(new DiscoverySubsystemDefinition());
        resourceRegistration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
    }

    @Override
    public void initializeParsers(final ExtensionParsingContext context) {
        for (DiscoverySubsystemSchema schema : EnumSet.allOf(DiscoverySubsystemSchema.class)) {
            context.setSubsystemXmlMapping(SUBSYSTEM_NAME, schema.getNamespace().getUri(), schema);
        }
    }
}
