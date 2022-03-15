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

import java.util.List;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * @author Tomaz Cerar (c) 2015 Red Hat Inc.
 */
public interface AttributeMarshallers {


    abstract static class MapAttributeMarshaller extends AttributeMarshaller {
        protected final String wrapperElement;
        protected final String elementName;
        protected final boolean wrapElement;

        public MapAttributeMarshaller(String wrapperElement, String elementName, boolean wrapElement) {
            this.wrapperElement = wrapperElement;
            this.elementName = elementName == null ? ModelDescriptionConstants.PROPERTY : elementName;
            this.wrapElement = wrapElement;
        }


        @Override
        public void marshallAsElement(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {

            resourceModel = resourceModel.get(attribute.getName());
            if (!resourceModel.isDefined()) {
                // nothing to do
                return;
            }

            String wrapper = wrapperElement == null ? attribute.getName() : wrapperElement;
            List<ModelNode> elementList = resourceModel.asList();
            if (elementList.isEmpty()) {
                if (wrapElement) {
                    writer.writeEmptyElement(wrapper);
                } else {
                    // This is a subtle programming error, where the xml schema doesn't
                    // prevent ambiguity between the 'null' and 'empty collection' cases,
                    // or a defined but empty ModelNode isn't valid but there's no
                    // AttributeDefinition validation preventing that being accepted.
                    // TODO We can look into possibly throwing an exception here, but
                    // for now to be conservative and avoid regressions I'm just sticking
                    // with existing behavior and marshalling nothing. I'll log a DEBUG
                    // though in the off chance it's helpful if this happens.
                    ControllerLogger.MGMT_OP_LOGGER.debugf("%s found ambigous empty value for unwrapped property %s",
                            getClass().getSimpleName(), attribute.getName());
                }
                // No elements to marshal, so we're done
                return;
            }

            if (wrapElement) {
                writer.writeStartElement(wrapper);
            }
            for (ModelNode property : elementList) {
                marshallSingleElement(attribute, property, marshallDefault, writer);
            }
            if (wrapElement) {
                writer.writeEndElement();
            }
        }

        @Override
        public void marshallAsAttribute(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
            marshallAsElement(attribute, resourceModel, marshallDefault, writer);
        }

        @Override
        public boolean isMarshallableAsElement() {
            return true;
        }

        public abstract void marshallSingleElement(AttributeDefinition attribute, ModelNode property, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException;

    }


    static class PropertiesAttributeMarshaller extends MapAttributeMarshaller {

        public PropertiesAttributeMarshaller(String wrapperElement, String elementName, boolean wrapElement) {
            super(wrapperElement, elementName, wrapElement);
        }

        public PropertiesAttributeMarshaller(String wrapperElement, boolean wrapElement) {
            this(wrapperElement, null, wrapElement);
        }

        public PropertiesAttributeMarshaller() {
            this(null, null, true);
        }

        @Override
        public void marshallSingleElement(AttributeDefinition attribute, ModelNode property, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
            writer.writeEmptyElement(elementName);
            writer.writeAttribute(org.jboss.as.controller.parsing.Attribute.NAME.getLocalName(), property.asProperty().getName());
            writer.writeAttribute(org.jboss.as.controller.parsing.Attribute.VALUE.getLocalName(), property.asProperty().getValue().asString());
        }
    }


    static class ObjectMapAttributeMarshaller extends MapAttributeMarshaller {
        protected final String keyAttributeName; //name of attribute to use for "key"

        public ObjectMapAttributeMarshaller(String wrapperElement, String elementName, boolean wrapElement, String keyAttributeName) {
            super(wrapperElement, elementName, wrapElement);
            this.keyAttributeName = keyAttributeName == null ? "key" : keyAttributeName;
        }

        public ObjectMapAttributeMarshaller(String wrapperElement, String elementName, boolean wrapElement) {
            this(wrapperElement, elementName, wrapElement, null);
        }

        public ObjectMapAttributeMarshaller(String wrapperElement, boolean wrapElement) {
            this(wrapperElement, null, wrapElement);
        }

        public ObjectMapAttributeMarshaller(String keyAttributeName) {
            this(null, null, true, keyAttributeName);
        }

        public ObjectMapAttributeMarshaller() {
            this(null, null, true);
        }

        @Override
        public void marshallSingleElement(AttributeDefinition attribute, ModelNode property, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
            ObjectMapAttributeDefinition map = ((ObjectMapAttributeDefinition) attribute);
            AttributeDefinition[] valueTypes = map.getValueType().getValueTypes();
            writer.writeEmptyElement(elementName);
            Property p = property.asProperty();
            writer.writeAttribute(keyAttributeName, p.getName());
            for (AttributeDefinition valueType : valueTypes) {
                valueType.getMarshaller().marshall(valueType, p.getValue(), false, writer);
            }
        }
    }

    class SimpleListAttributeMarshaller extends AttributeMarshaller {
        private final boolean wrap;

        SimpleListAttributeMarshaller(boolean wrap) {
            this.wrap = wrap;
        }

        @Override
        public boolean isMarshallableAsElement() {
            return true;
        }

        @Override
        public void marshallAsElement(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
            assert attribute instanceof SimpleListAttributeDefinition;
            SimpleListAttributeDefinition attr = (SimpleListAttributeDefinition) attribute;
            if (resourceModel.hasDefined(attribute.getName())) {
                if (wrap) {
                    writer.writeStartElement(attribute.getXmlName());
                }
                for (ModelNode handler : resourceModel.get(attribute.getName()).asList()) {
                    attr.getValueType().getMarshaller().marshallAsElement(attr.getValueType(), handler, true, writer);
                }
                if (wrap) {
                    writer.writeEndElement();
                }
            }
        }
    }

    static class NamedStringListMarshaller extends AttributeMarshaller.AttributeElementMarshaller {
        private final String xmlName;

        public NamedStringListMarshaller() {
            this.xmlName = null;
        }

        public NamedStringListMarshaller(String xmlName) {
            this.xmlName = xmlName;
        }

        @Override
        public void marshallAsElement(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
            assert attribute instanceof StringListAttributeDefinition;
            try {
                List<String> list = ((StringListAttributeDefinition) attribute).unwrap(ExpressionResolver.SIMPLE, resourceModel);
                if (list.isEmpty()) {
                    return;
                }
                if (resourceModel.hasDefined(attribute.getName())) {
                    for (ModelNode value : resourceModel.get(attribute.getName()).asList()) {
                        writer.writeStartElement(xmlName==null?attribute.getXmlName():xmlName);
                        writer.writeAttribute(ModelDescriptionConstants.NAME, value.asString());
                        writer.writeEndElement();
                    }
                }

            } catch (OperationFailedException e) {
                throw new XMLStreamException(e);
            }
        }
    }

    static AttributeMarshaller getObjectMapAttributeMarshaller(String keyElementName) {
        return new ObjectMapAttributeMarshaller(null, null, true, keyElementName);
    }

    static AttributeMarshaller getObjectMapAttributeMarshaller(String elementName, String keyElementName, boolean wrapElement) {
        return new ObjectMapAttributeMarshaller(null, elementName, wrapElement, keyElementName);
    }

    static AttributeMarshaller getObjectMapAttributeMarshaller(String elementName, boolean wrapElement) {
        return new ObjectMapAttributeMarshaller(null, elementName, wrapElement, null);
    }

    static AttributeMarshaller getObjectMapAttributeMarshaller(String wrapperElementName, boolean wrapElement, String elementName, String keyElementName) {
        return new ObjectMapAttributeMarshaller(wrapperElementName, elementName, wrapElement, keyElementName);
    }

    static AttributeMarshaller getSimpleListMarshaller(boolean wrapper) {
        return new SimpleListAttributeMarshaller(wrapper);
    }


    /**
     * simple marshaller
     */
    AttributeMarshaller SIMPLE = new DefaultAttributeMarshaller();

    /**
     * marshalls attributes to element where element name is attribute name and its content is value of attribute
     */
    AttributeMarshaller SIMPLE_ELEMENT = new AttributeMarshaller.WrappedSimpleAttributeMarshaller(false);

    /**
     * marshalls attributes to element where element name is attribute name and its content is value of resourceModel
     */
    AttributeMarshaller SIMPLE_ELEMENT_UNWRAP = new AttributeMarshaller.WrappedSimpleAttributeMarshaller(true);

    /**
     * space delimited list marshaller
     */
    AttributeMarshaller STRING_LIST = AttributeMarshaller.STRING_LIST;

    /**
     * comma delimited list marshaller
     */
    AttributeMarshaller STRING_LIST_COMMA_DELIMITED = AttributeMarshaller.COMMA_STRING_LIST;
    /**
     * String list marshaller that marshalls to named element list
     * example, name of element is attribute.getXmlName()
     * <principal name="John"/>
     * <principal name="Joe"/>
     */
    AttributeMarshaller STRING_LIST_NAMED_ELEMENT = new NamedStringListMarshaller();

    /**
     * Marshaller for ObjectTypeAttributeDefinition. The object and all its attributes will be marshalled as element only.
     */
    AttributeMarshaller OBJECT_ELEMENT_ONLY = new AttributeMarshaller.ObjectMarshaller(true);

    /**
     * Marshaller for ObjectTypeAttributeDefinition. The object and all its complex types descendants will get marshalled as elements whereas simple types will get marshalled as attributes.
     */
    AttributeMarshaller OBJECT_ATTRIBUTE = new AttributeMarshaller.ObjectMarshaller(false);


    AttributeMarshaller OBJECT_LIST_WRAPPED = new AttributeMarshaller.ObjectListMarshaller();
    AttributeMarshaller OBJECT_LIST_UNWRAPPED = new AttributeMarshaller.UnwrappedObjectListMarshaller();
    AttributeMarshaller OBJECT_LIST = OBJECT_LIST_WRAPPED;

    AttributeMarshaller OBJECT_MAP_MARSHALLER = new AttributeMarshallers.ObjectMapAttributeMarshaller();

    AttributeMarshaller PROPERTIES_WRAPPED = new AttributeMarshallers.PropertiesAttributeMarshaller();

    AttributeMarshaller PROPERTIES_UNWRAPPED = new AttributeMarshallers.PropertiesAttributeMarshaller(null, false);
}
