/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.extension.dependent;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * @author Brian Stansberry
 */
public class DependentExtension implements Extension {

    public static final String SUBSYSTEM_NAME = "dependent-extension";

    static final String NAMESPACE = "urn:jboss:domain:dependent-extension:1.0";

    @Override
    public void initialize(ExtensionContext context) {
        System.out.println("Initializing DependentExtension");
        SubsystemRegistration registration = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(1));
        ManagementResourceRegistration subsystem = registration.registerSubsystemModel(RootResourceDefinition.INSTANCE);
        subsystem.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE, false);
        registration.registerXMLElementWriter(SubsystemParser.INSTANCE);
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE, SubsystemParser.INSTANCE);
    }

}
