/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.mgmt.access.extension;


import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.test.integration.management.extension.EmptySubsystemParser;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Tomaz Cerar
 */
public class TestExtension implements Extension {

    private static final String SUBSYSTEM_NAME = "rbac";
    private static final String SUBSYSTEM_NAMESPACE = "urn:wildfly:test:test-extension:1.0";
    static final String MODULE_NAME = "org.wildfly.test.extension";


    @Override
    public void initialize(ExtensionContext context) {
        System.out.println("Initializing TestExtension");
        SubsystemRegistration registration = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(1, 0, 0));
        ManagementResourceRegistration rootRbacRegistration = registration.registerSubsystemModel(new RootResourceDefinition(SUBSYSTEM_NAME));
        rootRbacRegistration.registerSubModel(new ConstrainedResource(PathElement.pathElement("rbac-constrained")));
        rootRbacRegistration.registerSubModel(new SensitiveResource(PathElement.pathElement("rbac-sensitive")));
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, SUBSYSTEM_NAMESPACE, new EmptySubsystemParser(SUBSYSTEM_NAMESPACE));
    }

}
