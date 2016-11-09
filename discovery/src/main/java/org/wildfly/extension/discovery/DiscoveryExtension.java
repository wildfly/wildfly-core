/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

package org.wildfly.extension.discovery;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.wildfly.discovery.spi.DiscoveryProvider;

/**
 * The extension class for the WildFly Discovery extension.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class DiscoveryExtension implements Extension {

    // shared constants

    static final String SUBSYSTEM_NAME = "discovery";
    static final String NAMESPACE = "urn:jboss:domain:discovery:1.0";

    // XML and DMR name strings

    static final String ABSTRACT_TYPE = "abstract-type";
    static final String ABSTRACT_TYPE_AUTHORITY = "abstract-type-authority";
    static final String AGGREGATE_PROVIDER = "aggregate-provider";
    static final String ATTRIBUTE = "attribute";
    static final String ATTRIBUTES = "attributes";
    static final String DISCOVERY = "discovery";
    static final String NAME = "name";
    static final String PROVIDERS = "providers";
    static final String SERVICE = "service";
    static final String SERVICES = "services";
    static final String STATIC_PROVIDER = "static-provider";
    static final String URI = "uri";
    static final String URI_SCHEME_AUTHORITY = "uri-scheme-authority";
    static final String VALUE = "value";

    static final String RESOURCE_NAME = DiscoveryExtension.class.getPackage().getName() + ".LocalDescriptions";

    static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, SUBSYSTEM_NAME);

    static final String DISCOVERY_PROVIDER_CAPABILITY = "org.wildfly.discovery.provider";

    static final RuntimeCapability<?> DISCOVERY_PROVIDER_RUNTIME_CAPABILITY = RuntimeCapability.Builder.of(DISCOVERY_PROVIDER_CAPABILITY, true).setServiceType(DiscoveryProvider.class).build();

    // fields

    private final DiscoverySubsystemParser parser = new DiscoverySubsystemParser();

    /**
     * Construct a new instance.
     */
    public DiscoveryExtension() {
    }

    @Override
    public void initialize(final ExtensionContext context) {
        final SubsystemRegistration subsystemRegistration = context.registerSubsystem(SUBSYSTEM_NAME, ModelVersion.create(1, 0));
        subsystemRegistration.setHostCapable();
        subsystemRegistration.registerXMLElementWriter(parser);

        final ManagementResourceRegistration resourceRegistration = subsystemRegistration.registerSubsystemModel(DiscoverySubsystemDefinition.getInstance());
        resourceRegistration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
    }

    @Override
    public void initializeParsers(final ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE, parser);
    }

    static StandardResourceDescriptionResolver getResourceDescriptionResolver(final String... keyPrefixes) {
        StringBuilder sb = new StringBuilder(SUBSYSTEM_NAME);
        if (keyPrefixes != null) {
            for (String current : keyPrefixes) {
                sb.append(".").append(current);
            }
        }

        return new StandardResourceDescriptionResolver(sb.toString(), RESOURCE_NAME, DiscoveryExtension.class.getClassLoader(), true, false);
    }
}
