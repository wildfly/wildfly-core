/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.io;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.wildfly.extension.io.logging.IOLogger;


/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2012 Red Hat Inc.
 */
public class IOExtension implements Extension {

    public static final String SUBSYSTEM_NAME = "io";
    static final ModelVersion CURRENT_MODEL_VERSION = ModelVersion.create(5);
    protected static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME);
    protected static final PathElement BUFFER_POOL_PATH = PathElement.pathElement(Constants.BUFFER_POOL);
    protected static final PathElement WORKER_PATH = PathElement.pathElement(Constants.WORKER);
    private static final String RESOURCE_NAME = IOExtension.class.getPackage().getName() + ".LocalDescriptions";

    static final ModelNode NO_METRICS = new ModelNode(IOLogger.ROOT_LOGGER.noMetrics());

    public static StandardResourceDescriptionResolver getResolver(final String... keyPrefix) {
        StringBuilder prefix = new StringBuilder(SUBSYSTEM_NAME);
        for (String kp : keyPrefix) {
            prefix.append('.').append(kp);
        }
        return new StandardResourceDescriptionResolver(prefix.toString(), RESOURCE_NAME, IOExtension.class.getClassLoader(), true, false);
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.IO_1_0.getUriString(), IOSubsystemParser_1_0::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.IO_1_1.getUriString(), IOSubsystemParser_1_1::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.IO_2_0.getUriString(), IOSubsystemParser_2_0::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.IO_3_0.getUriString(), new IOSubsystemParser_3_0());
    }

    @Override
    public void initialize(ExtensionContext context) {
        final SubsystemRegistration subsystem = context.registerSubsystem(SUBSYSTEM_NAME, CURRENT_MODEL_VERSION);
        final ManagementResourceRegistration registration = subsystem.registerSubsystemModel(IORootDefinition.INSTANCE);
        registration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE, false);
        subsystem.registerXMLElementWriter(new IOSubsystemParser_3_0());
    }


}
