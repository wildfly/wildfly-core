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

import java.util.Iterator;

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
                builder.append(values.next().asString());
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

        @Override
        public void marshallAsElement(AttributeDefinition attribute, ModelNode resourceModel, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
            assert attribute instanceof ObjectTypeAttributeDefinition;
            if (resourceModel.hasDefined(attribute.getName())) {
                AttributeDefinition[] valueTypes = ((ObjectTypeAttributeDefinition) attribute).getValueTypes();
                writer.writeStartElement(attribute.getXmlName());
                for (AttributeDefinition valueType : valueTypes) {
                    if(resourceModel.hasDefined(attribute.getName(), valueType.getName())) {
                        ModelNode handler = resourceModel.get(attribute.getName());
                        if(marshallSimpleTypeAsElement) {
                            valueType.marshallAsElement(handler, marshallDefault, writer);
                        } else {
                            marshallAsAttribute(valueType, handler, marshallDefault, writer);
                        }
                    }
                }
                writer.writeEndElement();
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
}
