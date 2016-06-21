/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROPERTY;
import static org.jboss.as.controller.parsing.ParseUtils.requireAttributes;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public interface AttributeParsers {

    abstract class MapParser extends AttributeParser {
        protected final String wrapperElement;
        protected final String elementName;
        protected final boolean wrapElement;


        public MapParser(String wrapperElement, String elementName, boolean wrapElement) {
            this.wrapperElement = wrapperElement;
            this.elementName = elementName == null ? PROPERTY : elementName;
            this.wrapElement = wrapElement;
        }

        public MapParser(String wrapperElement, boolean wrapElement) {
            this(wrapperElement, PROPERTY, wrapElement);
        }

        public MapParser(boolean wrapElement) {
            this(null, null, wrapElement);
        }

        public MapParser(String wrapperElement) {
            this(wrapperElement, null, true);
        }

        public MapParser() {
            this(null, PROPERTY, true);
        }

        @Override
        public boolean isParseAsElement() {
            return true;
        }

        @Override
        public String getXmlName(AttributeDefinition attribute) {
            return wrapElement ? wrapperElement != null ? wrapperElement : attribute.getXmlName() : elementName;
        }

        @Override
        public void parseElement(AttributeDefinition attribute, XMLExtendedStreamReader reader, ModelNode operation) throws XMLStreamException {
            String wrapper = wrapperElement == null ? attribute.getName() : wrapperElement;
            assert attribute instanceof MapAttributeDefinition;
            MapAttributeDefinition mapAttribute = (MapAttributeDefinition) attribute;

            operation.get(attribute.getName()).setEmptyObject();//create empty attribute to address WFCORE-1448
            if (wrapElement) {
                if (!reader.getLocalName().equals(wrapper)) {
                    throw ParseUtils.unexpectedElement(reader, Collections.singleton(wrapper));
                } else {
                    // allow empty properties list
                    if (reader.nextTag() == XMLStreamConstants.END_ELEMENT) {
                        return;
                    }
                }
            }

            do {
                if (elementName.equals(reader.getLocalName())) {
                    //real parsing happens
                    parseSingleElement(mapAttribute, reader, operation);
                } else {
                    throw ParseUtils.unexpectedElement(reader, Collections.singleton(elementName));
                }

            } while (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT && reader.getLocalName().equals(elementName));

            if (wrapElement) {
                // To exit the do loop either we hit an END_ELEMENT or a START_ELEMENT not for 'elementName'
                // The latter means a bad document
                if (reader.getEventType() != XMLStreamConstants.END_ELEMENT) {
                    throw ParseUtils.unexpectedElement(reader, Collections.singleton(elementName));
                }
            }
        }

        public abstract void parseSingleElement(MapAttributeDefinition attribute, XMLExtendedStreamReader reader, ModelNode operation) throws XMLStreamException;

    }


    static class PropertiesParser extends MapParser {

        public PropertiesParser(String wrapperElement, String elementName, boolean wrapElement) {
            super(wrapperElement, elementName, wrapElement);
        }

        public PropertiesParser(String wrapperElement, boolean wrapElement) {
            this(wrapperElement, PROPERTY, wrapElement);
        }

        public PropertiesParser(boolean wrapElement) {
            this(null, null, wrapElement);
        }

        public PropertiesParser(String wrapperElement) {
            this(wrapperElement, null, true);
        }

        public PropertiesParser() {
            this(null, PROPERTY, true);
        }

        public void parseSingleElement(MapAttributeDefinition attribute, XMLExtendedStreamReader reader, ModelNode operation) throws XMLStreamException {
            assert attribute instanceof PropertiesAttributeDefinition;
            PropertiesAttributeDefinition property = (PropertiesAttributeDefinition) attribute;
            final String[] array = requireAttributes(reader, org.jboss.as.controller.parsing.Attribute.NAME.getLocalName(), org.jboss.as.controller.parsing.Attribute.VALUE.getLocalName());
            property.parseAndAddParameterElement(array[0], array[1], operation, reader);
            ParseUtils.requireNoContent(reader);
        }
    }

    static class ObjectMapParser extends MapParser {
        private final String keyAttributeName; //name of attribute to use for "key"

        public ObjectMapParser(String wrapperElement, String elementName, boolean wrapElement, String keyAttributeName) {
            super(wrapperElement, elementName, wrapElement);
            this.keyAttributeName = keyAttributeName == null ? "key" : keyAttributeName;
        }

        public ObjectMapParser() {
            this(null, PROPERTY, true, null);
        }

        public ObjectMapParser(boolean wrapElement) {
            this(null, null, wrapElement, null);
        }

        public ObjectMapParser(String elementName, boolean wrapElement) {
            this(null, elementName, wrapElement, null);
        }

        @Override
        public void parseSingleElement(MapAttributeDefinition attribute, XMLExtendedStreamReader reader, ModelNode operation) throws XMLStreamException {

            assert attribute instanceof ObjectMapAttributeDefinition;
            assert attribute.getParser().isParseAsElement();

            ObjectMapAttributeDefinition map = ((ObjectMapAttributeDefinition) attribute);
            ObjectTypeAttributeDefinition objectType = map.getValueType();
            ParseUtils.requireAttributes(reader, keyAttributeName);
            String key = reader.getAttributeValue(null, keyAttributeName);
            ModelNode op = operation.get(attribute.getName(), key);
            ObjectParser.parseEmbeddedElement(objectType, reader, op, keyAttributeName);
            ParseUtils.requireNoContent(reader);
        }
    }


    static class ObjectParser extends AttributeParser {
        @Override
        public boolean isParseAsElement() {
            return true;
        }

        @Override
        public void parseElement(AttributeDefinition attribute, XMLExtendedStreamReader reader, ModelNode operation) throws XMLStreamException {
            assert attribute instanceof ObjectTypeAttributeDefinition;

            if (attribute.getXmlName().equals(reader.getLocalName())) {
                ObjectTypeAttributeDefinition objectType = ((ObjectTypeAttributeDefinition) attribute);
                ModelNode op = operation.get(attribute.getName());
                parseEmbeddedElement(objectType, reader, op);
            } else {
                throw ParseUtils.unexpectedElement(reader, Collections.singleton(attribute.getXmlName()));
            }
            ParseUtils.requireNoContent(reader);
        }

        static void parseEmbeddedElement(ObjectTypeAttributeDefinition attribute, XMLExtendedStreamReader reader, ModelNode op, String... additionalExpectedAttributes) throws XMLStreamException {
            AttributeDefinition[] valueTypes = attribute.getValueTypes();

            Map<String, AttributeDefinition> attributes = Arrays.asList(valueTypes).stream()
                    .collect(Collectors.toMap(AttributeDefinition::getXmlName, Function.identity()));

            for (int i = 0; i < reader.getAttributeCount(); i++) {
                String attributeName = reader.getAttributeLocalName(i);
                String value = reader.getAttributeValue(i);
                if (attributes.containsKey(attributeName)) {
                    AttributeDefinition def = attributes.get(attributeName);
                    AttributeParser parser = def.getParser();
                    assert parser != null;
                    parser.parseAndSetParameter(def, value, op, reader);
                } else if (Arrays.binarySearch(additionalExpectedAttributes, attributeName) < 0) {
                    throw ParseUtils.unexpectedAttribute(reader, i, attributes.keySet());
                }
            }

        }

    }


    AttributeParser PROPERTIES_WRAPPED = new PropertiesParser();
    AttributeParser PROPERTIES_UNWRAPPED = new PropertiesParser(false);

    AttributeParser OBJECT_MAP_WRAPPED = new ObjectMapParser();
    AttributeParser OBJECT_MAP_UNWRAPPED = new ObjectMapParser(false);

    static AttributeParser getObjectMapAttributeParser(String keyElementName) {
        return new ObjectMapParser(null, null, true, keyElementName);
    }

    static AttributeParser getObjectMapAttributeParser(String elementName, String keyElementName, boolean wrapElement) {
        return new ObjectMapParser(null, elementName, wrapElement, keyElementName);
    }

    static AttributeParser getObjectMapAttributeParser(String elementName, boolean wrapElement) {
        return new ObjectMapParser(null, elementName, wrapElement, null);
    }

    static AttributeParser getObjectMapAttributeParser(String wrapperElementName, boolean wrapElement, String elementName, String keyElementName) {
        return new ObjectMapParser(wrapperElementName, elementName, wrapElement, keyElementName);
    }


}
