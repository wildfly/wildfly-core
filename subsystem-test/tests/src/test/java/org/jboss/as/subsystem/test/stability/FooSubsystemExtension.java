/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.stability;

import java.util.EnumSet;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.persistence.xml.SubsystemResourceXMLElementWriter;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * @author Paul Ferraro
 */
public class FooSubsystemExtension implements Extension {

    @Override
    public void initialize(ExtensionContext context) {
        SubsystemRegistration subsystem = context.registerSubsystem(FooSubsystemResourceDefinition.REGISTRATION.getName(), FooSubsystemModel.CURRENT.getVersion());
        ManagementResourceRegistration registration = subsystem.registerSubsystemModel(new FooSubsystemResourceDefinition());
        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);

        subsystem.registerXMLElementWriter(new SubsystemResourceXMLElementWriter(FooSubsystemSchema.CURRENT.get(context.getStability()).getSubsystemXMLElement()));
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMappings(FooSubsystemResourceDefinition.REGISTRATION.getName(), EnumSet.allOf(FooSubsystemSchema.class));
    }
}
