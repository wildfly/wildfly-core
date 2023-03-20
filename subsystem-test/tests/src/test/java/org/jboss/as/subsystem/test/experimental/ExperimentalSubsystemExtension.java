/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.experimental;

import java.util.EnumSet;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.FeatureStream;
import org.jboss.as.controller.PersistentResourceXMLDescriptionWriter;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * @author Paul Ferraro
 */
public class ExperimentalSubsystemExtension implements Extension {

    static final String SUBSYSTEM_NAME = "test";

    @Override
    public void initialize(ExtensionContext context) {
        FeatureStream stream = context.getFeatureStream();
        SubsystemRegistration subsystem = context.registerSubsystem(SUBSYSTEM_NAME, ExperimentalSubsystemModel.VERSION_1_0.getVersion());
        ManagementResourceRegistration registration = subsystem.registerSubsystemModel(new ExperimentalSubsystemResourceDefinition(stream));
        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);

        subsystem.registerXMLElementWriter(new PersistentResourceXMLDescriptionWriter(stream.enables(FeatureStream.EXPERIMENTAL) ? ExperimentalSubsystemSchema.VERSION_1_0_EXPERIMENTAL : ExperimentalSubsystemSchema.VERSION_1_0_STABLE));
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMappings(SUBSYSTEM_NAME, EnumSet.allOf(ExperimentalSubsystemSchema.class));
    }
}
