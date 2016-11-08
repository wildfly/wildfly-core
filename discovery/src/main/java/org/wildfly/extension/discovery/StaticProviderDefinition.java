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

import static org.wildfly.extension.discovery.DiscoveryExtension.DISCOVERY_PROVIDER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.discovery.DiscoveryExtension.STATIC_PROVIDER;

import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelType;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class StaticProviderDefinition extends SimpleResourceDefinition {

    static final SimpleAttributeDefinition ABSTRACT_TYPE = new SimpleAttributeDefinitionBuilder(DiscoveryExtension.ABSTRACT_TYPE, ModelType.STRING, true).setAllowExpression(true).build();
    static final SimpleAttributeDefinition ABSTRACT_TYPE_AUTHORITY = new SimpleAttributeDefinitionBuilder(DiscoveryExtension.ABSTRACT_TYPE_AUTHORITY, ModelType.STRING, true).setAllowExpression(true).build();
    static final SimpleAttributeDefinition URI = new SimpleAttributeDefinitionBuilder(DiscoveryExtension.URI, ModelType.STRING, false).setAllowExpression(true).build();
    static final SimpleAttributeDefinition URI_SCHEME_AUTHORITY = new SimpleAttributeDefinitionBuilder(DiscoveryExtension.URI_SCHEME_AUTHORITY, ModelType.STRING, true).setAllowExpression(true).build();

    static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(DiscoveryExtension.NAME, ModelType.STRING, false).setAllowExpression(true).build();
    static final SimpleAttributeDefinition VALUE = new SimpleAttributeDefinitionBuilder(DiscoveryExtension.VALUE, ModelType.STRING, true).setAllowExpression(true).build();

    static final ObjectTypeAttributeDefinition ATTRIBUTE = new ObjectTypeAttributeDefinition.Builder(DiscoveryExtension.ATTRIBUTE, NAME, VALUE).build();

    static final ObjectListAttributeDefinition ATTRIBUTES = new ObjectListAttributeDefinition.Builder(DiscoveryExtension.ATTRIBUTES, ATTRIBUTE)
            .setAttributeMarshaller(AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            .setAttributeParser(AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER)
            .build();

    static final ObjectTypeAttributeDefinition SERVICE = new ObjectTypeAttributeDefinition.Builder(DiscoveryExtension.SERVICE,
        ABSTRACT_TYPE,
        ABSTRACT_TYPE_AUTHORITY,
        URI,
        URI_SCHEME_AUTHORITY,
        ATTRIBUTES
    ).build();

    static final ObjectListAttributeDefinition SERVICES = new ObjectListAttributeDefinition.Builder(DiscoveryExtension.SERVICES, SERVICE).build();

    private static final ResourceDefinition INSTANCE = new StaticProviderDefinition();

    StaticProviderDefinition() {
        super(new Parameters(PathElement.pathElement(STATIC_PROVIDER), DiscoveryExtension.getResourceDescriptionResolver(STATIC_PROVIDER))
            .setAddHandler(StaticProviderAddHandler.getInstance())
            .setRemoveHandler(new TrivialRemoveStepHandler(DISCOVERY_PROVIDER_RUNTIME_CAPABILITY))
            .setCapabilities(DISCOVERY_PROVIDER_RUNTIME_CAPABILITY));
    }

    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(SERVICES, null, StaticProviderAddHandler::modifyRegistrationModel);
    }

    static ResourceDefinition getInstance() {
        return INSTANCE;
    }
}
