/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.threads;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.threads.CommonAttributes.THREADS;

import java.util.Collections;
import java.util.Set;

import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.extension.AbstractLegacyExtension;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * Extension for thread management.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 *
 * @deprecated the code module containing this extension only exists to support use of particular resource definition classes in other subsystems.
 */
@Deprecated(forRemoval = true)
public class ThreadsExtension extends AbstractLegacyExtension {

    public static final String SUBSYSTEM_NAME = "threads";
    static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME);

    static final String RESOURCE_NAME = ThreadsExtension.class.getPackage().getName() + ".LocalDescriptions";

    private static final int MANAGEMENT_API_MAJOR_VERSION = 2;
    private static final int MANAGEMENT_API_MINOR_VERSION = 0;
    private static final int MANAGEMENT_API_MICRO_VERSION = 0;
    static final ModelVersion DEPRECATED_SINCE = ModelVersion.create(1, 1, 0);

    private static final ModelVersion CURRENT_VERSION = ModelVersion.create(MANAGEMENT_API_MAJOR_VERSION, MANAGEMENT_API_MINOR_VERSION, MANAGEMENT_API_MICRO_VERSION);

    public ThreadsExtension() {
        super("org.jboss.as.threads", SUBSYSTEM_NAME);
    }

    @Override
    protected Set<ManagementResourceRegistration> initializeLegacyModel(ExtensionContext context) {

        final boolean registerRuntimeOnly = context.isRuntimeOnlyRegistrationValid();

        // Register the threads subsystem
        final SubsystemRegistration registration = context.registerSubsystem(THREADS, CURRENT_VERSION);
        registration.registerXMLElementWriter(ThreadsParser2_0::new);

        // Remoting threads description and operation handlers
        @SuppressWarnings({"removal"})
        final ManagementResourceRegistration subsystem = registration.registerSubsystemModel(new ThreadSubsystemResourceDefinition(registerRuntimeOnly));

        return Collections.singleton(subsystem);
    }

    @Override
    protected void initializeLegacyParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.CURRENT.getUriString(), ThreadsParser2_0::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.THREADS_1_1.getUriString(), ThreadsParser::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.THREADS_1_0.getUriString(), ThreadsParser::new);
    }
}
