/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a>
 */
public abstract class AttributeMarshaller {

    /**
     * Gets whether the given {@code resourceModel} has a value for this attribute that should be marshalled to XML.
     * <p>
     * This is the same as {@code isMarshallable(resourceModel, true)}.
     * </p>
     * @param attribute - attribute for which marshaling is being done
     * @param resourceModel the model, a non-null node of {@link org.jboss.dmr.ModelType#OBJECT}.
     * @return {@code true} if the given {@code resourceModel} has a defined value under this attribute's {@link AttributeDefinition#getName()} () name}.
     */
    public boolean isMarshallable(final AttributeDefinition attribute,final ModelNode resourceModel) {
        return isMarshallable(attribute,resourceModel, true);
    }

    /**
     * Gets whether the given {@code resourceModel} has a value for this attribute that should be marshalled to XML.
     *
     * @param attribute - attribute for which marshaling is being done
     * @param resourceModel   the model, a non-null node of {@link org.jboss.dmr.ModelType#OBJECT}.
     * @param marshallDefault {@code true} if the value should be marshalled even if it matches the default value
     * @return {@code true} if the given {@code resourceModel} has a defined value under this attribute's {@link AttributeDefinition#getName()} () name}
     *         and {@code marshallDefault} is {@code true} or that value differs from this attribute's {@link AttributeDefinition#getDefaultValue() default value}.
     */
    public boolean isMarshallable(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault) {
        return resourceModel.hasDefined(attribute.getName()) && (marshallDefault || !resourceModel.get(attribute.getName()).equals(attribute.getDefaultValue()));
    }

    /**
     * Marshalls the value from the given {@code resourceModel} as an xml element, if it
     * {@link #isMarshallable(AttributeDefinition, org.jboss.dmr.ModelNode, boolean) is marshallable}.
     *
     * @param attribute - attribute for which marshaling is being done
     * @param resourceModel the model, a non-null node of {@link org.jboss.dmr.ModelType#OBJECT}.
     * @param writer        stream writer to use for writing the attribute
     * @throws javax.xml.stream.XMLStreamException
     *          if thrown by {@code writer}
     */

    public void marshallAsAttribute(final AttributeDefinition attribute,final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException{
        throw ControllerLogger.ROOT_LOGGER.couldNotMarshalAttributeAsAttribute(attribute.getName());
    }

    public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException{
        throw ControllerLogger.ROOT_LOGGER.couldNotMarshalAttributeAsElement(attribute.getName());
    }

    public boolean isMarshallableAsElement(){
        return false;
    }


    public void marshall(final AttributeDefinition attribute,final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
        if (isMarshallableAsElement()){
            marshallAsElement(attribute,resourceModel, marshallDefault, writer);
        }else{
            marshallAsAttribute(attribute, resourceModel, marshallDefault, writer);
        }
    }

    private static class ListMarshaller extends DefaultAttributeMarshaller {
        private final char delimiter;

        ListMarshaller(char delimiter) {
            this.delimiter = delimiter;
        }

        @Override
        protected String asString(ModelNode value) {
            StringBuilder builder = new StringBuilder();
            Iterator<ModelNode> values = value.asList().iterator();
            while (values.hasNext()) {
                String elValue = values.next().asString();
                if (delimiter == ' '){
                    elValue = elValue.replaceAll(" ", String.valueOf((char)160));
                }
                builder.append(elValue);
                if (values.hasNext()) {
                    builder.append(this.delimiter);
                }
            }
            return builder.toString();
        }
    }

    private static class ObjectMarshaller extends DefaultAttributeMarshaller {
        private final boolean marshallSimpleTypeAsElement;

        private ObjectMarshaller(boolean marshallSimpleTypeAsAttribute) {
            this.marshallSimpleTypeAsElement = marshallSimpleTypeAsAttribute;
        }

        private static Set<AttributeDefinition> sortAttributes(AttributeDefinition[] attributes) {
            Set<AttributeDefinition> sortedAttrs = new LinkedHashSet<>(attributes.length);
            List<AttributeDefinition> elementAds = null;
            for (AttributeDefinition ad : attributes) {
                if (ad.getParser().isParseAsElement()) {
                    if (elementAds == null) {
                        elementAds = new ArrayList<>();
                    }
                    elementAds.add(ad);
                } else {
                    sortedAttrs.add(ad);
                }
            }
            if (elementAds != null) {
                sortedAttrs.addAll(elementAds);
            }
            return sortedAttrs;
        }

        @Override
        public void marshallAsElement(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
            assert attribute instanceof ObjectTypeAttributeDefinition;
            if (resourceModel.hasDefined(attribute.getName())) {
                AttributeDefinition[] valueTypes = ((ObjectTypeAttributeDefinition) attribute).getValueTypes();
                Set<AttributeDefinition> sortedAttrs = sortAttributes(valueTypes);
                writer.writeStartElement(attribute.getXmlName());
                for (AttributeDefinition valueType : sortedAttrs) {
                    if(resourceModel.hasDefined(attribute.getName(), valueType.getName())) {
                        ModelNode handler = resourceModel.get(attribute.getName());
                        if(marshallSimpleTypeAsElement) {
                            valueType.marshallAsElement(handler, marshallDefault, writer);
                        } else {
                            valueType.getMarshaller().marshall(valueType, handler, marshallDefault, writer);
                        }
                    }
                }
                writer.writeEndElement();
            }
        }

        @Override
        public boolean isMarshallableAsElement() {
            return !marshallSimpleTypeAsElement;
        }
    }


    private static class ObjectListMarshaller extends AttributeMarshaller {
        private ObjectListMarshaller() {
        }

        @Override
        public boolean isMarshallableAsElement() {
            return true;
        }

        ObjectTypeAttributeDefinition getObjectType(AttributeDefinition attribute) {
            assert attribute instanceof ObjectListAttributeDefinition;
            ObjectListAttributeDefinition list = ((ObjectListAttributeDefinition) attribute);
            return list.getValueType();
        }

        private boolean isMarshallable(AttributeDefinition[] valueTypes, ModelNode element){
            for (AttributeDefinition valueType : valueTypes) {
                if (valueType.getMarshaller().isMarshallable(valueType, element)){
                    return true;
                }
            }
            return false;
        }

        void writeElements(XMLStreamWriter writer, ObjectTypeAttributeDefinition objectType, AttributeDefinition[] valueTypes, List<ModelNode> elements) throws XMLStreamException {
            for (ModelNode element : elements) {
                if (isMarshallable(valueTypes, element)) {
                    writer.writeStartElement(objectType.getXmlName());
                    for (AttributeDefinition valueType : valueTypes) {
                        valueType.getMarshaller().marshall(valueType, element, false, writer);
                    }
                    writer.writeEndElement();
                }
            }
        }

        @Override
        public void marshallAsElement(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
            ObjectTypeAttributeDefinition objectType = getObjectType(attribute);
            AttributeDefinition[] valueTypes = objectType.getValueTypes();
            if (resourceModel.hasDefined(attribute.getName())) {
                List<ModelNode> elements = resourceModel.get(attribute.getName()).asList();
                if (elements.isEmpty()) {
                    writer.writeEmptyElement(attribute.getXmlName());
                } else {
                    writer.writeStartElement(attribute.getXmlName());
                    writeElements(writer, objectType, valueTypes, elements);
                    writer.writeEndElement();
                }
            }
        }
    }

    private static class UnwrappedObjectListMarshaller extends ObjectListMarshaller {
        private UnwrappedObjectListMarshaller() {
        }

        @Override
        public void marshallAsElement(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
            ObjectTypeAttributeDefinition objectType = getObjectType(attribute);
            AttributeDefinition[] valueTypes = objectType.getValueTypes();
            if (resourceModel.hasDefined(attribute.getName())) {
                List<ModelNode> elements = resourceModel.get(attribute.getName()).asList();
                writeElements(writer, objectType, valueTypes, elements);
            }
        }

    }

    /**
     * simple marshaller
     */
    public static final AttributeMarshaller SIMPLE = new DefaultAttributeMarshaller();

    /**
     * space delimited list marshaller
     */
    public static final AttributeMarshaller STRING_LIST = new ListMarshaller(' ');

    /**
     * comma delimited list marshaller
     */
    public static final AttributeMarshaller COMMA_STRING_LIST = new ListMarshaller(',');

    /**
     * Marshaller for ObjectTypeAttributeDefinition. The object and all its attributes will be marshalled as element only.
     */
    public static final AttributeMarshaller ELEMENT_ONLY_OBJECT = new ObjectMarshaller(true);

    /**
     * Marshaller for ObjectTypeAttributeDefinition. The object and all its complex types descendants will get marshalled as elements whereas simple types will get marshalled as attributes.
     */
    public static final AttributeMarshaller ATTRIBUTE_OBJECT = new ObjectMarshaller(false);


    public static final AttributeMarshaller WRAPPED_OBJECT_LIST_MARSHALLER = new ObjectListMarshaller();
    public static final AttributeMarshaller UNWRAPPED_OBJECT_LIST_MARSHALLER = new UnwrappedObjectListMarshaller();
    public static final AttributeMarshaller OBJECT_LIST_MARSHALLER = WRAPPED_OBJECT_LIST_MARSHALLER;

    public static final AttributeMarshaller OBJECT_MAP_MARSHALLER = new AttributeMarshallers.ObjectMapAttributeMarshaller();

    public static final AttributeMarshaller PROPERTIES_MARSHALLER = new AttributeMarshallers.PropertiesAttributeMarshaller();

    public static final AttributeMarshaller PROPERTIES_MARSHALLER_UNWRAPPED = new AttributeMarshallers.PropertiesAttributeMarshaller(null, false);

}
