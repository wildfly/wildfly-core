/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import static org.wildfly.extension.discovery.DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.ServiceRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.AttributeAccess.Flag;
import org.jboss.dmr.ModelType;

/**
 * Definition for static discovery provider resources.
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:paul.ferraro@redhat.com">Paul Ferraro</a>
 */
final class StaticDiscoveryProviderDefinition extends SimpleResourceDefinition {

    static final PathElement PATH = PathElement.pathElement("static-provider");

    static final SimpleAttributeDefinition ABSTRACT_TYPE = new SimpleAttributeDefinitionBuilder("abstract-type", ModelType.STRING, true).setAllowExpression(true).build();
    static final SimpleAttributeDefinition ABSTRACT_TYPE_AUTHORITY = new SimpleAttributeDefinitionBuilder("abstract-type-authority", ModelType.STRING, true).setAllowExpression(true).build();
    static final SimpleAttributeDefinition URI = new SimpleAttributeDefinitionBuilder("uri", ModelType.STRING, false).setValidator(new ServiceURIValidator()).setAllowExpression(true).build();
    static final SimpleAttributeDefinition URI_SCHEME_AUTHORITY = new SimpleAttributeDefinitionBuilder("uri-scheme-authority", ModelType.STRING, true).setAllowExpression(true).build();

    static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.NAME, ModelType.STRING, false).setAllowExpression(true).build();
    static final SimpleAttributeDefinition VALUE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.VALUE, ModelType.STRING, true).setAllowExpression(true).build();

    static final ObjectTypeAttributeDefinition ATTRIBUTE = new ObjectTypeAttributeDefinition.Builder("attribute", NAME, VALUE).build();

    static final ObjectListAttributeDefinition ATTRIBUTES = new ObjectListAttributeDefinition.Builder("attributes", ATTRIBUTE)
            .setAttributeMarshaller(AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            .setAttributeParser(AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER)
            .setRequired(false)
            .build();

    static final ObjectTypeAttributeDefinition SERVICE = new ObjectTypeAttributeDefinition.Builder("service",
        ABSTRACT_TYPE,
        ABSTRACT_TYPE_AUTHORITY,
        URI,
        URI_SCHEME_AUTHORITY,
        ATTRIBUTES
    ).build();

    static final ObjectListAttributeDefinition SERVICES = new ObjectListAttributeDefinition.Builder("services", SERVICE)
            .setAttributeMarshaller(AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            .setAttributeParser(AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER)
            .setFlags(Flag.RESTART_RESOURCE_SERVICES)
            .build();

    private static final AbstractAddStepHandler ADD_HANDLER = new StaticDiscoveryProviderAddHandler();

    StaticDiscoveryProviderDefinition() {
        super(new Parameters(PATH, DiscoveryExtension.SUBSYSTEM_RESOLVER.createChildResolver(PATH))
            .setAddHandler(ADD_HANDLER)
            .setRemoveHandler(new ServiceRemoveStepHandler(DiscoveryExtension.DISCOVERY_PROVIDER_CAPABILITY.getCapabilityServiceName(), ADD_HANDLER))
            .setCapabilities(DISCOVERY_PROVIDER_CAPABILITY));
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerReadWriteAttribute(SERVICES, null, ReloadRequiredWriteAttributeHandler.INSTANCE);
    }
}
