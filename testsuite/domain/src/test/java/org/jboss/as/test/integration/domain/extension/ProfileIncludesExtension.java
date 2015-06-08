/*
 * JBoss, Home of Professional Open Source.
 * Copyright ${year}, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
