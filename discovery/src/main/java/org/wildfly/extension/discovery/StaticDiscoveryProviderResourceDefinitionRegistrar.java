/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.discovery;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceRegistration;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.AttributeAccess.Flag;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.discovery.AttributeValue;
import org.wildfly.discovery.ServiceURL;
import org.wildfly.discovery.impl.StaticDiscoveryProvider;
import org.wildfly.discovery.spi.DiscoveryProvider;
import org.wildfly.subsystem.service.ServiceDependency;

/**
 * Registers the static discovery provider resource definition.
 * @author Paul Ferraro
 */
public class StaticDiscoveryProviderResourceDefinitionRegistrar extends DiscoveryProviderResourceDefinitionRegistrar {

    static final ResourceRegistration REGISTRATION = ResourceRegistration.of(PathElement.pathElement("static-provider"));

    private static final SimpleAttributeDefinition ABSTRACT_TYPE = new SimpleAttributeDefinitionBuilder("abstract-type", ModelType.STRING, true).setAllowExpression(true).build();
    private static final SimpleAttributeDefinition ABSTRACT_TYPE_AUTHORITY = new SimpleAttributeDefinitionBuilder("abstract-type-authority", ModelType.STRING, true).setAllowExpression(true).build();
    private static final SimpleAttributeDefinition URI = new SimpleAttributeDefinitionBuilder("uri", ModelType.STRING, false).setValidator(new ServiceURIValidator()).setAllowExpression(true).build();
    private static final SimpleAttributeDefinition URI_SCHEME_AUTHORITY = new SimpleAttributeDefinitionBuilder("uri-scheme-authority", ModelType.STRING, true).setAllowExpression(true).build();

    private static final SimpleAttributeDefinition NAME = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.NAME, ModelType.STRING, false).setAllowExpression(true).build();
    private static final SimpleAttributeDefinition VALUE = new SimpleAttributeDefinitionBuilder(ModelDescriptionConstants.VALUE, ModelType.STRING, true).setAllowExpression(true).build();

    private static final ObjectTypeAttributeDefinition ATTRIBUTE = new ObjectTypeAttributeDefinition.Builder("attribute", NAME, VALUE).build();

    private static final ObjectListAttributeDefinition SERVICE_ATTRIBUTES = new ObjectListAttributeDefinition.Builder("attributes", ATTRIBUTE)
            .setAttributeMarshaller(AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            .setAttributeParser(AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER)
            .setRequired(false)
            .build();

    private static final ObjectTypeAttributeDefinition SERVICE = new ObjectTypeAttributeDefinition.Builder("service",
            ABSTRACT_TYPE,
            ABSTRACT_TYPE_AUTHORITY,
            URI,
            URI_SCHEME_AUTHORITY,
            SERVICE_ATTRIBUTES
        ).build();

    static final ObjectListAttributeDefinition SERVICES = new ObjectListAttributeDefinition.Builder("services", SERVICE)
            .setAttributeMarshaller(AttributeMarshaller.UNWRAPPED_OBJECT_LIST_MARSHALLER)
            .setAttributeParser(AttributeParser.UNWRAPPED_OBJECT_LIST_PARSER)
            .setFlags(Flag.RESTART_RESOURCE_SERVICES)
            .build();

    StaticDiscoveryProviderResourceDefinitionRegistrar() {
        super(REGISTRATION, List.of(SERVICES));
    }

    @Override
    public ServiceDependency<DiscoveryProvider> resolve(OperationContext context, ModelNode model) throws OperationFailedException {
        List<ModelNode> services = SERVICES.resolveModelAttribute(context, model).asListOrEmpty();
        List<ServiceURL> serviceURLs = new ArrayList<>(services.size());
        for (ModelNode service : services) {
            ServiceURL.Builder builder = new ServiceURL.Builder();
            builder.setUri(java.net.URI.create(URI.resolveModelAttribute(context, service).asString()));
            String abstractType = ABSTRACT_TYPE.resolveModelAttribute(context, service).asStringOrNull();
            if (abstractType != null) {
                builder.setAbstractType(abstractType);
            }
            String abstractTypeAuthority = ABSTRACT_TYPE_AUTHORITY.resolveModelAttribute(context, service).asStringOrNull();
            if (abstractTypeAuthority != null) {
                builder.setAbstractTypeAuthority(abstractTypeAuthority);
            }
            String uriSchemeAuthority = URI_SCHEME_AUTHORITY.resolveModelAttribute(context, service).asStringOrNull();
            if (uriSchemeAuthority != null) {
                builder.setUriSchemeAuthority(uriSchemeAuthority);
            }
            for (ModelNode attribute : SERVICE_ATTRIBUTES.resolveModelAttribute(context, service).asListOrEmpty()) {
                String name = NAME.resolveModelAttribute(context, attribute).asString();
                String value = VALUE.resolveModelAttribute(context, attribute).asStringOrNull();
                if (value != null) {
                    builder.addAttribute(name, AttributeValue.fromString(value));
                } else {
                    builder.addAttribute(name);
                }
            }
            ServiceURL serviceURL = builder.create();
            Messages.log.tracef("Adding service URL %s", serviceURL);
            serviceURLs.add(serviceURL);
        }
        return ServiceDependency.of(new StaticDiscoveryProvider(serviceURLs));
    }
}