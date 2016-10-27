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

            String wrapper = wrapperElement == null ? attribute.getXmlName() : wrapperElement;
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
                valueType.getAttributeMarshaller().marshall(valueType, p.getValue(), false, writer);
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
}
