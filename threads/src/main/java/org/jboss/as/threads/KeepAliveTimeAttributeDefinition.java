/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;

import java.util.EnumSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.DefaultAttributeMarshaller;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PropagatingCorrector;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * {@link org.jboss.as.controller.AttributeDefinition} for a thread pool resource's keepalive-time attribute.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
class KeepAliveTimeAttributeDefinition extends ObjectTypeAttributeDefinition {

    static final SimpleAttributeDefinition KEEPALIVE_TIME_TIME = new SimpleAttributeDefinitionBuilder(CommonAttributes.TIME, ModelType.LONG, false)
            .setAllowExpression(true)
            .setXmlName("time")
            .build();

    static final SimpleAttributeDefinition KEEPALIVE_TIME_UNIT = new SimpleAttributeDefinitionBuilder(CommonAttributes.UNIT, ModelType.STRING, false)
            .setXmlName("unit")
            .setAllowExpression(true)
            .setValidator(EnumValidator.create(TimeUnit.class))
            .setAttributeMarshaller( new DefaultAttributeMarshaller(){
                @Override
                public void marshallAsAttribute(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
                    writer.writeAttribute(attribute.getXmlName(), resourceModel.get(attribute.getName()).asString().toLowerCase(Locale.ENGLISH));
                }
            })
            .build();

    KeepAliveTimeAttributeDefinition() {
        super(Builder.of(CommonAttributes.KEEPALIVE_TIME, KEEPALIVE_TIME_TIME, KEEPALIVE_TIME_UNIT)
                        .setCorrector(PropagatingCorrector.INSTANCE)
                        .setXmlName("keepalive-time")
                        .setAttributeParser(AttributeParser.OBJECT_PARSER)
                        .setAttributeMarshaller(AttributeMarshaller.ATTRIBUTE_OBJECT)
        );
    }


    @Override
    protected void addValueTypeDescription(ModelNode node, String prefix, ResourceBundle bundle, boolean forOperation, final ResourceDescriptionResolver resolver, Locale locale) {
        // Swap out the resolver to use the threadpool.common keys
        ResourceDescriptionResolver override = new StandardResourceDescriptionResolver("threadpool.common", "", getClass().getClassLoader()) {
            @Override
            public ResourceBundle getResourceBundle(Locale locale) {
                return resolver.getResourceBundle(locale);
            }
        };
        super.addValueTypeDescription(node, prefix, bundle, forOperation, override, locale);
    }

    public void parseAndSetParameter(final ModelNode operation, final XMLExtendedStreamReader reader) throws XMLStreamException {

        ModelNode model = new ModelNode();

        final int attrCount = reader.getAttributeCount();
        Set<Attribute> required = EnumSet.of(Attribute.TIME, Attribute.UNIT);
        for (int i = 0; i < attrCount; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case TIME: {
                    KEEPALIVE_TIME_TIME.parseAndSetParameter(value, model, reader);
                    break;
                }
                case UNIT: {
                    KEEPALIVE_TIME_UNIT.parseAndSetParameter(value, model, reader);
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }

        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }

        ParseUtils.requireNoContent(reader);

        operation.get(getName()).set(model);
    }
}