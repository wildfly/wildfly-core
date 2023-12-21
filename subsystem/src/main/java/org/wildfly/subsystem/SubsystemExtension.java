/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.subsystem;

import java.util.Optional;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.services.path.PathManager;
import org.wildfly.subsystem.resource.ManagementResourceRegistrationContext;

/**
 * Generic extension implementation that registers a single subsystem.
 * @author Paul Ferraro
 */
public class SubsystemExtension<S extends SubsystemSchema<S>> implements Extension {

    private final SubsystemConfiguration configuration;
    private final SubsystemPersistence<S> persistence;

    public SubsystemExtension(SubsystemConfiguration configuration, SubsystemPersistence<S> persistence) {
        this.configuration = configuration;
        this.persistence = persistence;
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
        registration.registerXMLElementWriter(this.persistence.getWriter());
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMappings(this.configuration.getName(), this.persistence.getSchemas());
    }
}
