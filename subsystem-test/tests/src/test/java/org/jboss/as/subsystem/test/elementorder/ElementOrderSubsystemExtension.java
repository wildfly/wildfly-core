/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.elementorder;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PersistentResourceXMLDescriptionWriter;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

import java.util.EnumSet;

public class ElementOrderSubsystemExtension implements Extension {

    @Override
    public void initialize(ExtensionContext context) {
        SubsystemRegistration subsystem = context.registerSubsystem(ElementOrderSubsystemResourceDefinition.SUBSYSTEM_NAME, ModelVersion.create(1));
        ManagementResourceRegistration registration = subsystem.registerSubsystemModel(new ElementOrderSubsystemResourceDefinition());
        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);

        subsystem.registerXMLElementWriter(new PersistentResourceXMLDescriptionWriter(ElementOrderSubsystemSchema.VERSION_1_0));
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMappings(ElementOrderSubsystemResourceDefinition.SUBSYSTEM_NAME, EnumSet.of(ElementOrderSubsystemSchema.VERSION_1_0));
    }
}
