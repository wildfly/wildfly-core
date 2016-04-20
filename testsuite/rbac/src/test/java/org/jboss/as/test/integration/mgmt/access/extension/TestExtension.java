/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
