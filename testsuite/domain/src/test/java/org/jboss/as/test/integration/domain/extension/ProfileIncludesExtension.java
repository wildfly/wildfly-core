/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.domain.extension;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;

/**
 * @author Kabir Khan
 */
public class ProfileIncludesExtension implements Extension {
    static final String MODULE_NAME = "org.wildfly.extension.profile-includes-test";
    private static final EmptySubsystemParser PARSER1 = new EmptySubsystemParser("urn:wildfly:extension:profile-includes-test-one:1.0");
    private static final EmptySubsystemParser PARSER2 = new EmptySubsystemParser("urn:wildfly:extension:profile-includes-test-two:1.0");
    private static final EmptySubsystemParser PARSER3 = new EmptySubsystemParser("urn:wildfly:extension:profile-includes-test-three:1.0");
    @Override
    public void initialize(ExtensionContext context) {
        registerSubsystem(context, "one", PARSER1);
        registerSubsystem(context, "two", PARSER2);
        registerSubsystem(context, "three", PARSER3);
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping("one", PARSER1.getNamespace(), PARSER1);
        context.setSubsystemXmlMapping("two", PARSER2.getNamespace(), PARSER2);
        context.setSubsystemXmlMapping("three", PARSER3.getNamespace(), PARSER3);
    }

    private void registerSubsystem(ExtensionContext context, String subsystemName, EmptySubsystemParser parser) {
        final SubsystemRegistration subsystem = context.registerSubsystem(subsystemName, ModelVersion.create(1, 0, 0));
        ManagementResourceRegistration registration =
                subsystem.registerSubsystemModel(VersionedExtensionCommon.createResourceDefinition(PathElement.pathElement("subsystem", subsystemName)));
        subsystem.registerXMLElementWriter(parser);
        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
    }
}
