/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem;

import java.util.Optional;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.xml.Schema;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;

/**
 * Generic extension implementation that registers a single subsystem.
 * @author Paul Ferraro
 */
public class SubsystemExtension<S extends Schema> implements Extension {

    private final SubsystemConfiguration configuration;
    private final SubsystemPersistence<S> persistenceConfiguration;

    public SubsystemExtension(SubsystemConfiguration configuration, SubsystemPersistence<S> persistenceConfiguration) {
        this.configuration = configuration;
        this.persistenceConfiguration = persistenceConfiguration;
    }

    @Override
    public void initialize(ExtensionContext context) {
        SubsystemRegistration registration = context.registerSubsystem(this.configuration.getName(), this.configuration.getModel().getVersion());
        ManagementResourceRegistrationContext registrationContext = new ManagementResourceRegistrationContext() {
            @Override
            public boolean isRuntimeOnlyRegistrationValid() {
                return context.isRuntimeOnlyRegistrationValid();
            }

            @Override
            public Optional<PathManager> getPathManager() {
                return context.getProcessType().isServer() ? Optional.of(context.getPathManager()) : Optional.empty();
            }
        };
        // Auto-register subsystem describe handler
        this.configuration.getRegistrar().register(registration, registrationContext).registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
        registration.registerXMLElementWriter(this.persistenceConfiguration.getWriter());
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        for (S schema : this.persistenceConfiguration.getSchemas()) {
            context.setSubsystemXmlMapping(this.configuration.getName(), schema.getNamespace().getUri(), this.persistenceConfiguration.getReader(schema));
        }
    }
}
