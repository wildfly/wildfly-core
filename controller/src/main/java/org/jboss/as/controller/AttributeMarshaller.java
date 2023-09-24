/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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

    /**
     * Applies standard element text formatting such that multiline content is on a new line
     * from the element wrapping the text and is indented one level. Single-line content is
     * on the same line as the element.
     *
     * @param content the content. Cannot be {@code null}
     * @param writer the writer. Cannot be {@code null}
     */
    @SuppressWarnings("WeakerAccess")
    public static void marshallElementContent(String content, XMLStreamWriter writer) throws XMLStreamException {
        if (content.indexOf('\n') > -1) {
            // Multiline content. Use the overloaded variant that staxmapper will format
            writer.writeCharacters(content);
        } else {
            // Staxmapper will just output the chars without adding newlines if this is used
            char[] chars = content.toCharArray();
            writer.writeCharacters(chars, 0, chars.length);
        }
    }

    static class ListMarshaller extends DefaultAttributeMarshaller {
        private final char delimiter;

        ListMarshaller(char delimiter) {
            this.delimiter = delimiter;
        }

        @Override
        protected String asString(ModelNode value) {
            StringBuilder builder = new StringBuilder();
            Iterator<ModelNode> values = value.asList().iterator();
            while (values.hasNext()) {
                builder.append(values.next().asString());
                if (values.hasNext()) {
                    builder.append(this.delimiter);
                }
            }
            return builder.toString();
        }
    }
    /*
    Sorts attributes so that xsd:attribute ones come first
     */
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



    static class ObjectMarshaller extends DefaultAttributeMarshaller {
        private final boolean marshallSimpleTypeAsElement;

        ObjectMarshaller(boolean marshallSimpleTypeAsAttribute) {
            this.marshallSimpleTypeAsElement = marshallSimpleTypeAsAttribute;
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


    static class ObjectListMarshaller extends AttributeMarshaller {
        ObjectListMarshaller() {
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
                    Set<AttributeDefinition> sortedAttrs = sortAttributes(valueTypes);
                    for (AttributeDefinition valueType : sortedAttrs) {
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

    static class UnwrappedObjectListMarshaller extends ObjectListMarshaller {
        UnwrappedObjectListMarshaller() {
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
     * Version of marshaller that by default marshalls to element
     */
    public abstract static class AttributeElementMarshaller extends AttributeMarshaller{
        public abstract void marshallAsElement(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException;

        @Override
        public boolean isMarshallableAsElement() {
            return true;
        }

        @Override
        public boolean isMarshallable(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault) {
            return resourceModel.hasDefined(attribute.getName());
        }
    }

    static class WrappedSimpleAttributeMarshaller extends AttributeElementMarshaller {
        final boolean unwrap;
        WrappedSimpleAttributeMarshaller(boolean unwrap) {
            this.unwrap = unwrap;
        }

          @Override
          public void marshallAsElement(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
              writer.writeStartElement(attribute.getXmlName());
              marshallElementContent(unwrap ? resourceModel.asString() : resourceModel.get(attribute.getName()).asString(), writer);
              writer.writeEndElement();
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
