/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2016 Red Hat inc.
 */
public class CoreManagementExtension implements Extension {
    public static final String SUBSYSTEM_NAME = "core-management";
    static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME);
    static final PathElement PROCESS_STATE_LISTENER_PATH = PathElement.pathElement("process-state-listener");

    static final String RESOURCE_NAME = CoreManagementExtension.class.getPackage().getName() + ".LocalDescriptions";

    private static final ModelVersion CURRENT_VERSION = ModelVersion.create(1, 0, 0);

    public static ResourceDescriptionResolver getResourceDescriptionResolver(final String... keyPrefix) {
        StringBuilder prefix = new StringBuilder(SUBSYSTEM_NAME);
        for (String kp : keyPrefix) {
            if (prefix.length() > 0){
                prefix.append('.');
            }
            prefix.append(kp);
        }
        return new StandardResourceDescriptionResolver(prefix.toString(), RESOURCE_NAME, CoreManagementExtension.class.getClassLoader(), true, false);
    }

    @Override
    public void initialize(ExtensionContext context) {
        final SubsystemRegistration subsystem = context.registerSubsystem(SUBSYSTEM_NAME, CURRENT_VERSION);
        subsystem.registerXMLElementWriter(CoreManagementSubsystemParser_1_0::new);
        //This subsystem should be runnable on a host
        subsystem.setHostCapable();
        ManagementResourceRegistration registration = subsystem.registerSubsystemModel(new CoreManagementRootResourceDefinition());
        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        // For the current version we don't use a Supplier as we want its description initialized
        // TODO if any new xsd versions are added, use a Supplier for the old version
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, CoreManagementSubsystemParser_1_0.NAMESPACE, new CoreManagementSubsystemParser_1_0());
    }
}
