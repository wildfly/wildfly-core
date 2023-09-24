/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.AttributeParsers.ObjectParser.parseEmbeddedElement;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROPERTY;
import static org.jboss.as.controller.parsing.ParseUtils.requireAttributes;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public interface AttributeParsers {

    abstract class AttributeElementParser extends AttributeParser {
        private final String xmlName;
        public AttributeElementParser() {
                    this.xmlName = null;
        }

        public AttributeElementParser(String xmlName) {
            this.xmlName = xmlName;
        }

        @Override
        public boolean isParseAsElement() {
            return true;
        }

        @Override
        public String getXmlName(AttributeDefinition attribute) {
            return xmlName == null ? attribute.getXmlName() : xmlName;
        }

        @Override
        public abstract void parseElement(AttributeDefinition attribute, XMLExtendedStreamReader reader, ModelNode operation) throws XMLStreamException;
    }

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
                    if (reader.nextTag() == END_ELEMENT) {
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

            } while (reader.hasNext() && reader.nextTag() != END_ELEMENT && reader.getLocalName().equals(elementName));

            if (wrapElement) {
                // To exit the do loop either we hit an END_ELEMENT or a START_ELEMENT not for 'elementName'
                // The latter means a bad document
                if (reader.getEventType() != END_ELEMENT) {
                    throw ParseUtils.unexpectedElement(reader, Collections.singleton(elementName));
                }
            }
        }

        public abstract void parseSingleElement(MapAttributeDefinition attribute, XMLExtendedStreamReader reader, ModelNode operation) throws XMLStreamException;

    }


    class PropertiesParser extends MapParser {

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
            final String[] array = requireAttributes(reader, org.jboss.as.controller.parsing.Attribute.NAME.getLocalName(), org.jboss.as.controller.parsing.Attribute.VALUE.getLocalName());
            attribute.parseAndAddParameterElement(array[0], array[1], operation, reader);
            ParseUtils.requireNoContent(reader);
        }
    }

    class ObjectMapParser extends MapParser {
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
            parseEmbeddedElement(objectType, reader, op, keyAttributeName);
            ParseUtils.requireNoContent(reader);
        }
    }


    class ObjectParser extends AttributeParser {
        @Override
        public boolean isParseAsElement() {
            return true;
        }

        @Override
        public void parseElement(AttributeDefinition attribute, XMLExtendedStreamReader reader, ModelNode operation) throws XMLStreamException {
            assert attribute instanceof ObjectTypeAttributeDefinition;

            if (operation.hasDefined(attribute.getName())) {
                throw ParseUtils.unexpectedElement(reader);
            }
            if (attribute.getXmlName().equals(reader.getLocalName())) {
                ObjectTypeAttributeDefinition objectType = ((ObjectTypeAttributeDefinition) attribute);
                ModelNode op = operation.get(attribute.getName());
                op.setEmptyObject();
                parseEmbeddedElement(objectType, reader, op);
            } else {
                throw ParseUtils.unexpectedElement(reader, Collections.singleton(attribute.getXmlName()));
            }
            if (!reader.isEndElement()) {
                ParseUtils.requireNoContent(reader);
            }
        }

        static void parseEmbeddedElement(ObjectTypeAttributeDefinition attribute, XMLExtendedStreamReader reader, ModelNode op, String... additionalExpectedAttributes) throws XMLStreamException {
            AttributeDefinition[] valueTypes = attribute.getValueTypes();

            Map<String, AttributeDefinition> attributes = new HashMap<>();
            for (AttributeDefinition valueType : valueTypes) {
                attributes.put(valueType.getXmlName(), valueType);
            }

            Map<String, AttributeDefinition> attributeElements = new HashMap<>();
            for (AttributeDefinition attributeDefinition : valueTypes) {
                if (attributeDefinition.getParser().isParseAsElement()) {
                    attributeElements.put(attributeDefinition.getParser().getXmlName(attributeDefinition), attributeDefinition);
                }
            }

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
            // Check if there are also element attributes inside a group
            if (!attributeElements.isEmpty()) {
                while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                    String attrName = reader.getLocalName();
                    if (attributeElements.containsKey(attrName)) {
                        AttributeDefinition ad = attributeElements.get(reader.getLocalName());
                        ad.getParser().parseElement(ad, reader, op);
                    } else {
                        throw ParseUtils.unexpectedElement(reader, attributeElements.keySet());
                    }
                }
            }

        }

    }

    class WrappedObjectListParser extends AttributeParser {
        @Override
        public boolean isParseAsElement() {
            return true;
        }


        ObjectTypeAttributeDefinition getObjectType(AttributeDefinition attribute) {
            assert attribute instanceof ObjectListAttributeDefinition;
            ObjectListAttributeDefinition list = ((ObjectListAttributeDefinition) attribute);
            return list.getValueType();
        }

        @Override
        public void parseElement(AttributeDefinition attribute, XMLExtendedStreamReader reader, ModelNode operation) throws XMLStreamException {
            assert attribute instanceof ObjectListAttributeDefinition;

            ObjectListAttributeDefinition list = ((ObjectListAttributeDefinition) attribute);
            ObjectTypeAttributeDefinition objectType = list.getValueType();


            ModelNode listValue = new ModelNode();
            listValue.setEmptyList();
            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                if (objectType.getXmlName().equals(reader.getLocalName())) {
                    ModelNode op = listValue.add();
                    parseEmbeddedElement(objectType, reader, op);
                } else {
                    throw ParseUtils.unexpectedElement(reader, Collections.singleton(objectType.getXmlName()));
                }
                if (!reader.isEndElement()) {
                    ParseUtils.requireNoContent(reader);
                }
            }
            operation.get(attribute.getName()).set(listValue);
        }
    }

    class UnWrappedObjectListParser extends WrappedObjectListParser {

        @Override
        public String getXmlName(AttributeDefinition attribute) {
            return getObjectType(attribute).getXmlName();
        }

        @Override
        public void parseElement(AttributeDefinition attribute, XMLExtendedStreamReader reader, ModelNode operation) throws XMLStreamException {
            ObjectTypeAttributeDefinition objectType = getObjectType(attribute);

            ModelNode listValue = operation.get(attribute.getName());
            if (!listValue.isDefined()){
                listValue.setEmptyList();
            }
            String xmlName = objectType.getXmlName();
            if (xmlName.equals(reader.getLocalName())) {
                ModelNode op = listValue.add();
                parseEmbeddedElement(objectType, reader, op);
            } else {
                throw ParseUtils.unexpectedElement(reader, Collections.singleton(xmlName));
            }
            if (!reader.isEndElement()) {
                ParseUtils.requireNoContent(reader);
            }
        }
    }

    class NamedStringListParser extends AttributeParsers.AttributeElementParser {
        public NamedStringListParser() {
        }

        public NamedStringListParser(String xmlName) {
            super(xmlName);
        }

        @Override
        public void parseElement(AttributeDefinition ad, XMLExtendedStreamReader reader, ModelNode addPermissionMapper) throws XMLStreamException {
            ParseUtils.requireSingleAttribute(reader, NAME);
            String name = reader.getAttributeValue(0);
            addPermissionMapper.get(ad.getName()).add(name);
            ParseUtils.requireNoContent(reader);
        }
    }

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

    /**
     * Simple attribute parser, that loads attribute from xml attribute
     */
    AttributeParser SIMPLE = AttributeParser.SIMPLE;
    /**
     * simple parser that loads attribute value from xml element with name of attribute and takes its content as value of attribute.
     */
    AttributeParser SIMPLE_ELEMENT = new AttributeParser.WrappedSimpleAttributeParser();


    AttributeParser PROPERTIES_WRAPPED = new PropertiesParser();
    AttributeParser PROPERTIES_UNWRAPPED = new PropertiesParser(false);

    AttributeParser OBJECT_MAP_WRAPPED = new ObjectMapParser();
    AttributeParser OBJECT_MAP_UNWRAPPED = new ObjectMapParser(false);

    AttributeParser WRAPPED_OBJECT_LIST_PARSER = new WrappedObjectListParser();
    AttributeParser UNWRAPPED_OBJECT_LIST_PARSER = new UnWrappedObjectListParser();

    AttributeParser STRING_LIST_NAMED_ELEMENT = new NamedStringListParser();
    AttributeParser STRING_LIST = AttributeParser.STRING_LIST;
    AttributeParser STRING_LIST_COMMA_DELIMITED = AttributeParser.COMMA_DELIMITED_STRING_LIST;


}
