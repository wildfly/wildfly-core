/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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
 */
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
        @SuppressWarnings("deprecation")
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
