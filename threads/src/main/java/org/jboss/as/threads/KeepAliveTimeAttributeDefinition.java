/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.threads.CommonAttributes.TIME;
import static org.jboss.as.threads.CommonAttributes.UNIT;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PropagatingCorrector;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
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
            .setXmlName("keep-alive-time")
            .build();

    static final SimpleAttributeDefinition KEEPALIVE_TIME_UNIT = new SimpleAttributeDefinitionBuilder(CommonAttributes.UNIT, ModelType.STRING, false)
            .setXmlName("keep-alive-unit")
            .setAllowExpression(true)
            .setValidator(new EnumValidator<TimeUnit>(TimeUnit.class, false, true))
            .build();


    static final RejectAttributeChecker TRANSFORMATION_CHECKER;

    static {

        Map<String, RejectAttributeChecker> fieldCheckers = new HashMap<String, RejectAttributeChecker>();
        fieldCheckers.put(KeepAliveTimeAttributeDefinition.KEEPALIVE_TIME_TIME.getName(), RejectAttributeChecker.SIMPLE_EXPRESSIONS);
        fieldCheckers.put(KeepAliveTimeAttributeDefinition.KEEPALIVE_TIME_UNIT.getName(), RejectAttributeChecker.SIMPLE_EXPRESSIONS);
        TRANSFORMATION_CHECKER = new RejectAttributeChecker.ObjectFieldsRejectAttributeChecker(fieldCheckers);
    }

    KeepAliveTimeAttributeDefinition() {
        super(Builder.of(CommonAttributes.KEEPALIVE_TIME, KEEPALIVE_TIME_TIME, KEEPALIVE_TIME_UNIT)
                        .setCorrector(PropagatingCorrector.INSTANCE)
                        .setXmlName("keep-alive-time")
                        .setAttributeMarshaller(new KeepAliveTimeMarshaller())
                        .setAttributeParser(new KeepAliveTimeParser())


        );
    }


    @Override
    protected void addValueTypeDescription(ModelNode node, String prefix, ResourceBundle bundle, final ResourceDescriptionResolver resolver, Locale locale) {
        // Swap out the resolver to use the threadpool.common keys
        ResourceDescriptionResolver override = new StandardResourceDescriptionResolver("threadpool.common", "", getClass().getClassLoader()) {
            @Override
            public ResourceBundle getResourceBundle(Locale locale) {
                return resolver.getResourceBundle(locale);
            }
        };
        super.addValueTypeDescription(node, prefix, bundle, override, locale);
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

    private static class KeepAliveTimeParser extends AttributeParser {
        @Override
        public void parseAndSetParameter(AttributeDefinition attribute, String value, ModelNode operation, final XMLStreamReader reader) throws XMLStreamException {
            ModelNode model = parse(attribute, value, reader);
            operation.get(attribute.getName()).set(model);
        }

        @Override
        public ModelNode parse(AttributeDefinition attribute, String value, XMLStreamReader readerOrig) throws XMLStreamException {
            final XMLExtendedStreamReader reader = (XMLExtendedStreamReader) readerOrig;
            ModelNode model = new ModelNode();

            final int attrCount = reader.getAttributeCount();
            Set<Attribute> required = EnumSet.of(Attribute.TIME, Attribute.UNIT);
            for (int i = 0; i < attrCount; i++) {
                requireNoNamespaceAttribute(reader, i);
                value = reader.getAttributeValue(i);

                String attributeName = reader.getAttributeLocalName(i);
                switch (attributeName) {
                    case "keep-alive-time": {
                        KEEPALIVE_TIME_TIME.parseAndSetParameter(value, model, reader);
                        required.remove(Attribute.TIME);
                        break;
                    }
                    case "keep-alive-unit": {
                        KEEPALIVE_TIME_UNIT.parseAndSetParameter(value, model, reader);
                        required.remove(Attribute.UNIT);
                        break;
                    }
                }
            }

            if (!required.isEmpty()) {
                throw missingRequired(reader, required);
            }

            return model;
        }
    }

    private static class KeepAliveTimeMarshaller extends AttributeMarshaller {

        @Override
        public void marshallAsAttribute(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
            ModelNode keepalive = resourceModel.get(attribute.getName());
            if (keepalive.hasDefined(TIME)) {
                writer.writeAttribute(KeepAliveTimeAttributeDefinition.KEEPALIVE_TIME_TIME.getXmlName(), keepalive.get(TIME).asString());
            }
            if (keepalive.hasDefined(UNIT)) {
                writer.writeAttribute(KeepAliveTimeAttributeDefinition.KEEPALIVE_TIME_UNIT.getXmlName(), keepalive.get(UNIT).asString().toLowerCase(Locale.ENGLISH));
            }
        }


    }
}
