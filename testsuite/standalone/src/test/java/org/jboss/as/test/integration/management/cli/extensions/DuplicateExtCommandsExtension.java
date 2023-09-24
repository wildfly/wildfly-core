/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.cli.extensions;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.parsing.ExtensionParsingContext;

/**
 * @author Petr Kremensky pkremens@redhat.com
 */
public class DuplicateExtCommandsExtension implements Extension {

    public static final String SUBSYSTEM_NAME = "test-cli-duplicate-commands";

    @Override
    public void initialize(ExtensionContext context) {
        SubsystemRegistration registration = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(1));
        registration.registerSubsystemModel(new DuplicateExtCommandSubsystemResourceDescription());
        registration.registerXMLElementWriter(new CliExtCommandsParser());
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        //Don't need a parser, just register a dummy writer in the initialize() method
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, CliExtCommandsParser.NAMESPACE, new CliExtCommandsParser());
    }
}
