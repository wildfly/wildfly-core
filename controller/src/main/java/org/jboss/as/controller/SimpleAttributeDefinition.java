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

import org.jboss.as.controller.client.helpers.MeasurementUnit;
import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.registry.AttributeAccess;
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

    // NOTE: Standards for creating a constructor variant are:
    // 1) Expected to be a common use case; no one-offs.
    // 2) Max 4 parameters, or 5 only if the fifth is "AttributeAccess.Flag... flags"
    // 3) No single type appears twice in the param list. Hence no allowNull, allowExpressions variants
    //
    // All other use cases should use the constructor that takes a builder


    /**
     * Creates a new attribute definition.
     *
     * @param name the name of the attribute. Cannot be {@code null}
     * @param type the type of the attribute value. Cannot be {@code null}
     * @param allowNull {@code true} if {@link org.jboss.dmr.ModelType#UNDEFINED} is a valid type for the value
     */
    public SimpleAttributeDefinition(final String name, final ModelType type, final boolean allowNull) {
        super(name, name, null, type, allowNull, false, null, null, null, true, null, null,
                null, false, null, null, null, null);
    }

    /**
     * Creates a new attribute definition.
     *
     * @param name the name of the attribute. Cannot be {@code null}
     * @param type the type of the attribute value. Cannot be {@code null}
     * @param allowNull {@code true} if {@link org.jboss.dmr.ModelType#UNDEFINED} is a valid type for the value
     * @param flags any flags to indicate special characteristics of the attribute
     */
    public SimpleAttributeDefinition(final String name, final ModelType type, final boolean allowNull, final AttributeAccess.Flag... flags) {
        super(name, name, null, type, allowNull, false, null, null, null, true, null, null,
                null, false, null, null, null, null, flags);
    }

    /**
     * Creates a new attribute definition.
     *
     * @param name the name of the attribute. Cannot be {@code null}
     * @param type the type of the attribute value. Cannot be {@code null}
     * @param allowNull {@code true} if {@link org.jboss.dmr.ModelType#UNDEFINED} is a valid type for the value
     * @param measurementUnit a measurement unit for the attribute's value. Can be {@code null}
     */
    public SimpleAttributeDefinition(final String name, final ModelType type, final boolean allowNull, final MeasurementUnit measurementUnit) {
        super(name, name, null, type, allowNull, false, measurementUnit, null, null, true, null, null,
                null, false, null, null, null, null);
    }

    /**
     * Creates a new attribute definition.
     *
     * @param name the name of the attribute. Cannot be {@code null}
     * @param type the type of the attribute value. Cannot be {@code null}
     * @param allowNull {@code true} if {@link org.jboss.dmr.ModelType#UNDEFINED} is a valid type for the value
     * @param measurementUnit a measurement unit for the attribute's value. Can be {@code null}
     * @param flags any flags to indicate special characteristics of the attribute
     */
    public SimpleAttributeDefinition(final String name, final ModelType type, final boolean allowNull,
                                     final MeasurementUnit measurementUnit, final AttributeAccess.Flag... flags) {
        super(name, name, null, type, allowNull, false, measurementUnit, null, null, true, null, null,
                null, false, null, null, null, null, flags);
    }

    /**
     * Creates a new attribute definition.
     *
     * @param name the name of the attribute. Cannot be {@code null}
     * @param defaultValue a default value to use for the attribute if none is specified by the user. Can be {@code null}
     * @param type the type of the attribute value. Cannot be {@code null}
     * @param allowNull {@code true} if {@link org.jboss.dmr.ModelType#UNDEFINED} is a valid type for the value
     */
    public SimpleAttributeDefinition(final String name, final ModelNode defaultValue, final ModelType type, final boolean allowNull) {
        super(name, name, defaultValue, type, allowNull, false, null, null, null, true, null, null,
                null, false, null, null, null, null);
    }

    /**
     * Creates a new attribute definition.
     *
     * @param name the name of the attribute. Cannot be {@code null}
     * @param defaultValue a default value to use for the attribute if none is specified by the user. Can be {@code null}
     * @param type the type of the attribute value. Cannot be {@code null}
     * @param allowNull {@code true} if {@link org.jboss.dmr.ModelType#UNDEFINED} is a valid type for the value
     * @param flags any flags to indicate special characteristics of the attribute
     */
    public SimpleAttributeDefinition(final String name, final ModelNode defaultValue, final ModelType type,
                                     final boolean allowNull, final AttributeAccess.Flag... flags) {
        super(name, name, defaultValue, type, allowNull, false, null, null, null, true, null, null,
                null, false, null, null, null, null, flags);
    }

    protected SimpleAttributeDefinition(AbstractAttributeDefinitionBuilder<?, ? extends SimpleAttributeDefinition> builder) {
        super(builder);
    }

    /**
     * Creates and returns a {@link org.jboss.dmr.ModelNode} using the given {@code value} after first validating the node
     * against {@link #getValidator() this object's validator}.
     * <p>
     * If {@code value} is {@code null} an {@link ModelType#UNDEFINED undefined} node will be returned.
     * </p>
     *
     * @param value the value. Will be {@link String#trim() trimmed} before use if not {@code null}.
     * @param reader {@link XMLStreamReader} from which the {@link XMLStreamReader#getLocation() location} from which
     *               the attribute value was read can be obtained and used in any {@code XMLStreamException}, in case
     *               the given value is invalid.
     * @return {@code ModelNode} representing the parsed value
     *
     * @throws javax.xml.stream.XMLStreamException if {@code value} is not valid
     *
     * @see #parseAndSetParameter(String, ModelNode, XMLStreamReader)
     */
    public ModelNode parse(final String value, final XMLStreamReader reader) throws XMLStreamException {
        try {
            return parse(this, this.getValidator(), value);
        } catch (OperationFailedException e) {
            throw new XMLStreamException(e.getFailureDescription().toString(), reader.getLocation());
        }
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
        ModelNode paramVal = parse(value, reader);
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
        attributeMarshaller.marshallAsAttribute(this, resourceModel, marshallDefault, writer);
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
        attributeMarshaller.marshallAsElement(this, resourceModel, marshallDefault, writer);
    }

    static ModelNode parse(AttributeDefinition attribute, ParameterValidator validator, final String value) throws OperationFailedException  {
        ModelNode node = ParseUtils.parseAttributeValue(value, attribute.isAllowExpression(), attribute.getType());
        validator.validateParameter(attribute.getXmlName(), node);
        return node;
    }

}
