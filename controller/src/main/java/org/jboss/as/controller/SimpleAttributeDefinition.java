/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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


import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;


/**
 * Defining characteristics of an attribute in a {@link org.jboss.as.controller.registry.Resource} or a
 * parameter or reply value type field in an {@link org.jboss.as.controller.OperationDefinition}, with utility
 * methods for conversion to and from xml and for validation.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class SimpleAttributeDefinition extends AttributeDefinition {

    protected SimpleAttributeDefinition(AbstractAttributeDefinitionBuilder<?, ? extends SimpleAttributeDefinition> builder) {
        super(builder);
    }

    /**
     * Creates a {@link ModelNode} using the given {@code value} after first validating the node
     * against {@link #getValidator() this object's validator}, and then stores it in the given {@code operation}
     * model node as a key/value pair whose key is this attribute's {@link #getName() name}.
     * <p>
     * If {@code value} is {@code null} an {@link ModelType#UNDEFINED undefined} node will be stored if such a value
     * is acceptable to the validator.
     * </p>
     * <p>
     * The expected usage of this method is in parsers seeking to build up an operation to store their parsed data
     * into the configuration.
     * </p>
     *
     * @param value the value. Will be {@link String#trim() trimmed} before use if not {@code null}.
     * @param operation model node of type {@link ModelType#OBJECT} into which the parsed value should be stored
     * @param reader {@link XMLStreamReader} from which the {@link XMLStreamReader#getLocation() location} from which
     *               the attribute value was read can be obtained and used in any {@code XMLStreamException}, in case
     *               the given value is invalid.
     * @throws XMLStreamException if {@code value} is not valid
     */
    public void parseAndSetParameter(final String value, final ModelNode operation, final XMLStreamReader reader) throws XMLStreamException {
        ModelNode paramVal = getParser().parse(this, value, reader);
        operation.get(getName()).set(paramVal);
    }

    /**
     * Marshalls the value from the given {@code resourceModel} as an xml attribute, if it
     * {@link #isMarshallable(org.jboss.dmr.ModelNode, boolean) is marshallable}.
     * <p>
     * Invoking this method is the same as calling {@code marshallAsAttribute(resourceModel, true, writer)}
     * </p>
     *
     * @param resourceModel the model, a non-null node of {@link org.jboss.dmr.ModelType#OBJECT}.
     * @param writer stream writer to use for writing the attribute
     * @throws javax.xml.stream.XMLStreamException if {@code writer} throws an exception
     */
    public void marshallAsAttribute(final ModelNode resourceModel, final XMLStreamWriter writer) throws XMLStreamException {
        marshallAsAttribute(resourceModel, true, writer);
    }

    /**
     * Marshalls the value from the given {@code resourceModel} as an xml attribute, if it
     * {@link #isMarshallable(org.jboss.dmr.ModelNode, boolean) is marshallable}.
     * @param resourceModel the model, a non-null node of {@link org.jboss.dmr.ModelType#OBJECT}.
     * @param marshallDefault {@code true} if the value should be marshalled even if it matches the default value
     * @param writer stream writer to use for writing the attribute
     * @throws javax.xml.stream.XMLStreamException if {@code writer} throws an exception
     */
    public void marshallAsAttribute(final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
        getMarshaller().marshallAsAttribute(this, resourceModel, marshallDefault, writer);
    }

    /**
     * {@inheritDoc}
     *
     * This implementation marshalls the attribute value as text content of the element.
     * @param marshallDefault
     * @throws javax.xml.stream.XMLStreamException
     */
    @Override
    public void marshallAsElement(final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
        getMarshaller().marshallAsElement(this, resourceModel, marshallDefault, writer);
    }

}
