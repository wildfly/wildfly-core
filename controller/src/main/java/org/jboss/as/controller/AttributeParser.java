/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;


import java.util.Collections;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.controller.operations.validation.ParameterValidator;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 */
public abstract class AttributeParser {

    /**
     * Creates a {@link org.jboss.dmr.ModelNode} using the given {@code value} after first validating the node
     * against {@link org.jboss.as.controller.AttributeDefinition#getValidator() this object's validator}., and then stores it in the given {@code operation}
     * model node as a key/value pair whose key is this attribute's getName() name}.
     * <p>
     * If {@code value} is {@code null} an {@link org.jboss.dmr.ModelType#UNDEFINED undefined} node will be stored if such a value
     * is acceptable to the validator.
     * </p>
     * <p>
     * The expected usage of this method is in parsers seeking to build up an operation to store their parsed data
     * into the configuration.
     * </p>
     *
     * @param value     the value. Will be {@link String#trim() trimmed} before use if not {@code null}.
     * @param operation model node of type {@link org.jboss.dmr.ModelType#OBJECT} into which the parsed value should be stored
     * @param reader    {@link javax.xml.stream.XMLStreamReader} from which the {@link javax.xml.stream.XMLStreamReader#getLocation() location} from which
     *                  the attribute value was read can be obtained and used in any {@code XMLStreamException}, in case
     *                  the given value is invalid.
     * @throws javax.xml.stream.XMLStreamException if {@code value} is not valid
     */
    public void parseAndSetParameter(final AttributeDefinition attribute, final String value, final ModelNode operation, final XMLStreamReader reader) throws XMLStreamException {
        ModelNode paramVal = parse(attribute, value, reader);
        operation.get(attribute.getName()).set(paramVal);
    }

    /**
     * Creates and returns a {@link org.jboss.dmr.ModelNode} using the given {@code value} after first validating the node
     * against {@link org.jboss.as.controller.AttributeDefinition#getValidator() this object's validator}.
     * <p>
     * If {@code value} is {@code null} an {@link org.jboss.dmr.ModelType#UNDEFINED undefined} node will be returned.
     * </p>
     *
     * @param value  the value. Will be {@link String#trim() trimmed} before use if not {@code null}.
     * @param reader {@link XMLStreamReader} from which the {@link XMLStreamReader#getLocation() location} from which
     *               the attribute value was read can be obtained and used in any {@code XMLStreamException}, in case
     *               the given value is invalid.
     * @return {@code ModelNode} representing the parsed value
     * @throws javax.xml.stream.XMLStreamException if {@code value} is not valid
     * @see #parseAndSetParameter(org.jboss.as.controller.AttributeDefinition, String, ModelNode, XMLStreamReader)
     */
    public ModelNode parse(final AttributeDefinition attribute, final String value, final XMLStreamReader reader) throws XMLStreamException {
        try {
            return parse(attribute, value);
        } catch (OperationFailedException e) {
            throw new XMLStreamException(e.getFailureDescription().toString(), reader.getLocation());
        }
    }

    private ModelNode parse(final AttributeDefinition attribute, final String value) throws OperationFailedException {
        ModelNode node = ParseUtils.parseAttributeValue(value, attribute.isAllowExpression(), attribute.getType());
        final ParameterValidator validator;
        // A bit yuck, but I didn't want to introduce a new type just for this
        if (attribute instanceof ListAttributeDefinition) {
            validator = ((ListAttributeDefinition) attribute).getElementValidator();
        } else if (attribute instanceof MapAttributeDefinition) {
            validator = ((MapAttributeDefinition) attribute).getElementValidator();
        } else {
            validator = attribute.getValidator();
        }
        validator.validateParameter(attribute.getXmlName(), node);

        return node;
    }

    public boolean isParseAsElement() {
        return false;
    }

    public void parseElement(final AttributeDefinition attribute, final XMLExtendedStreamReader reader, ModelNode operation) throws XMLStreamException {

    }

    public String getXmlName(final AttributeDefinition attribute){
        return attribute.getXmlName();
    }

    public static final AttributeParser SIMPLE = new AttributeParser() {
    };

    public static final AttributeParser STRING_LIST = new AttributeParser() {
        @Override
        public void parseAndSetParameter(AttributeDefinition attribute, String value, ModelNode operation, XMLStreamReader reader) throws XMLStreamException {
            if (value == null) return;
            final ModelNode node = operation.get(attribute.getName()).setEmptyList();
            if (!value.isEmpty()) {
                for (final String element : value.split("\\s+")) {
                    node.add(parse(attribute, element, reader));
                }
            }
        }
    };

    public static final AttributeParser COMMA_DELIMITED_STRING_LIST = new AttributeParser() {
        @Override
        public void parseAndSetParameter(AttributeDefinition attribute, String value, ModelNode operation, XMLStreamReader reader) throws XMLStreamException {
            if (value == null) return;
            final ModelNode node = operation.get(attribute.getName()).setEmptyList();
            if (!value.isEmpty()) {
                for (String element : value.split(",")) {
                    node.add(parse(attribute, element, reader));
                }
            }
        }
    };

    static class WrappedSimpleAttributeParser extends AttributeParser {

            @Override
            public boolean isParseAsElement() {
                return true;
            }

            @Override
            public void parseElement(AttributeDefinition attribute, XMLExtendedStreamReader reader, ModelNode operation) throws XMLStreamException {
                assert attribute instanceof SimpleAttributeDefinition;
                if (operation.hasDefined(attribute.getName())) {
                    throw ParseUtils.unexpectedElement(reader);
                } else if (attribute.getXmlName().equals(reader.getLocalName())) {
                    ((SimpleAttributeDefinition) attribute).parseAndSetParameter(reader.getElementText(), operation, reader);
                } else {
                    throw ParseUtils.unexpectedElement(reader, Collections.singleton(attribute.getXmlName()));
                }
            }
        }

    public static final class DiscardOldDefaultValueParser extends AttributeParser{
        private final String value;

        public DiscardOldDefaultValueParser(String value) {
            this.value = value;
        }

        @Override
        public ModelNode parse(AttributeDefinition attribute, String value, XMLStreamReader reader) throws XMLStreamException {
            if (!this.value.equals(value)) { //if default value set, ignore it!
                return super.parse(attribute, value, reader);
            }
            return new ModelNode();
        }
    }

    public static final AttributeParser PROPERTIES_PARSER = new AttributeParsers.PropertiesParser();

    public static final AttributeParser PROPERTIES_PARSER_UNWRAPPED = new AttributeParsers.PropertiesParser(false);

    public static final AttributeParser OBJECT_PARSER = new AttributeParsers.ObjectParser();

    public static final AttributeParser OBJECT_LIST_PARSER = AttributeParsers.WRAPPED_OBJECT_LIST_PARSER;

    public static final AttributeParser WRAPPED_OBJECT_LIST_PARSER = AttributeParsers.WRAPPED_OBJECT_LIST_PARSER;
    public static final AttributeParser UNWRAPPED_OBJECT_LIST_PARSER = AttributeParsers.UNWRAPPED_OBJECT_LIST_PARSER;

}
