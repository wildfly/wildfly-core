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
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;

/**
 * Fake extension to use in testing extension management.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public class TestExtension implements Extension {

    public static final String MODULE_NAME = "org.jboss.as.test.extension";

    private final EmptySubsystemParser parserOne = new EmptySubsystemParser("urn:jboss:test:extension:1:1.0");
    private final EmptySubsystemParser parserTwo = new EmptySubsystemParser("urn:jboss:test:extension:2:1.0");


    @Override
    public void initialize(ExtensionContext context) {
        SubsystemRegistration one = context.registerSubsystem("1", ModelVersion.create(1, 1, 1));
        one.registerXMLElementWriter(parserOne);
        ManagementResourceRegistration mrrOne = one.registerSubsystemModel(new RootResourceDefinition("1"));
        mrrOne.registerSubModel(new ConstrainedResource(PathElement.pathElement("rbac-constrained")));
        mrrOne.registerSubModel(new SensitiveResource(PathElement.pathElement("rbac-sensitive")));


        SubsystemRegistration two = context.registerSubsystem("2", ModelVersion.create(2, 2, 2));
        two.registerXMLElementWriter(parserTwo);
        ManagementResourceRegistration mrrTwo = two.registerSubsystemModel(new RootResourceDefinition("2"));
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping("1", parserOne.getNamespace(), parserOne);
        context.setSubsystemXmlMapping("2", parserTwo.getNamespace(), parserTwo);
    }
}
