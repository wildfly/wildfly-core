/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem.resource;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.TreeMap;
import java.util.function.Consumer;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.MapAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.xml.XMLCardinality;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.wildfly.common.function.ExceptionBiConsumer;
import org.wildfly.common.function.ExceptionFunction;

/**
 * {@link MapAttributeDefinition} for maps with keys and values of type {@link ModelType#STRING}.
 * @author Paul Ferraro
 */
public class PropertiesAttributeDefinition extends MapAttributeDefinition implements ResourceModelResolver<Map<String, String>> {
    public interface PropertyPersistence {
        ExceptionFunction<XMLExtendedStreamReader, Map.Entry<String, String>, XMLStreamException> getPropertyReader();
        ExceptionBiConsumer<XMLStreamWriter, Map.Entry<String, String>, XMLStreamException> getPropertyWriter();
    }

    private final Consumer<ModelNode> descriptionContributor;

    PropertiesAttributeDefinition(Builder builder) {
        super(builder);
        this.descriptionContributor = node -> {
            node.get(ModelDescriptionConstants.VALUE_TYPE).set(ModelType.STRING);
            node.get(ModelDescriptionConstants.EXPRESSIONS_ALLOWED).set(new ModelNode(this.isAllowExpression()));
        };
    }

    @Override
    public Map<String, String> resolve(OperationContext context, ModelNode model) throws OperationFailedException {
        List<Property> properties = this.resolveModelAttribute(context, model).asPropertyListOrEmpty();
        if (properties.isEmpty()) return Map.of();
        Map<String, String> result = new TreeMap<>();
        for (Property property : properties) {
            result.put(property.getName(), property.getValue().asString());
        }
        return result;
    }

    @Override
    protected void addValueTypeDescription(ModelNode node, ResourceBundle bundle) {
        this.descriptionContributor.accept(node);
    }

    @Override
    protected void addAttributeValueTypeDescription(ModelNode node, ResourceDescriptionResolver resolver, Locale locale, ResourceBundle bundle) {
        this.descriptionContributor.accept(node);
    }

    @Override
    protected void addOperationParameterValueTypeDescription(ModelNode node, String operationName, ResourceDescriptionResolver resolver, Locale locale, ResourceBundle bundle) {
        this.descriptionContributor.accept(node);
    }

    /**
     * Parses/marshals properties as a repeating XML element of the form:
     * <pre><property name="foo" value="bar"/></pre>
     */
    public static class PropertyValueAttributePersistence implements PropertyPersistence {
        private final ExceptionFunction<XMLExtendedStreamReader, Map.Entry<String, String>, XMLStreamException> reader;
        private final ExceptionBiConsumer<XMLStreamWriter, Map.Entry<String, String>, XMLStreamException> writer;

        /**
         * Creates properties persistence using the specified local names of the key and value attributes.
         * @param keyLocalName the local name of the property key attribute
         * @param valueLocalName the local name of the property value attribute
         */
        public PropertyValueAttributePersistence(String keyLocalName, String valueLocalName) {
            this.reader = new ExceptionFunction<>() {
                @Override
                public Map.Entry<String, String> apply(XMLExtendedStreamReader reader) throws XMLStreamException {
                    String[] attributes = ParseUtils.requireAttributes(reader, Attribute.NAME.getLocalName(), Attribute.VALUE.getLocalName());
                    ParseUtils.requireNoContent(reader);
                    String key = attributes[0];
                    String value = attributes[1];
                    return Map.entry(key, value);
                }
            };
            this.writer = new ExceptionBiConsumer<>() {
                @Override
                public void accept(XMLStreamWriter writer, Map.Entry<String, String> entry) throws XMLStreamException {
                    writer.writeAttribute(keyLocalName, entry.getKey());
                    writer.writeAttribute(valueLocalName, entry.getValue());
                }
            };
        }

        @Override
        public ExceptionFunction<XMLExtendedStreamReader, Entry<String, String>, XMLStreamException> getPropertyReader() {
            return this.reader;
        }

        @Override
        public ExceptionBiConsumer<XMLStreamWriter, Entry<String, String>, XMLStreamException> getPropertyWriter() {
            return this.writer;
        }
    }

    /**
     * Parses/marshals properties as a repeating XML element of the form:
     * <pre><property name="foo">bar</property></pre>
     */
    public static class PropertyValueContextPersistence implements PropertyPersistence {
        private final ExceptionFunction<XMLExtendedStreamReader, Map.Entry<String, String>, XMLStreamException> reader;
        private final ExceptionBiConsumer<XMLStreamWriter, Map.Entry<String, String>, XMLStreamException> writer;

        /**
         * Creates properties persistence using the specified attribute local name of the key attribute
         * @param keyLocalName the local name of the property key attribute
         */
        public PropertyValueContextPersistence(String keyLocalName) {
            this.reader = new ExceptionFunction<>() {
                @Override
                public Map.Entry<String, String> apply(XMLExtendedStreamReader reader) throws XMLStreamException {
                    String[] attributes = ParseUtils.requireAttributes(reader, Attribute.NAME.getLocalName());
                    String key = attributes[0];
                    String value = reader.getElementText();
                    return Map.entry(key, value);
                }
            };
            this.writer = new ExceptionBiConsumer<>() {
                @Override
                public void accept(XMLStreamWriter writer, Map.Entry<String, String> entry) throws XMLStreamException {
                    writer.writeAttribute(keyLocalName, entry.getKey());
                    AttributeMarshaller.marshallElementContent(entry.getValue(), writer);
                }
            };
        }

        @Override
        public ExceptionFunction<XMLExtendedStreamReader, Entry<String, String>, XMLStreamException> getPropertyReader() {
            return this.reader;
        }

        @Override
        public ExceptionBiConsumer<XMLStreamWriter, Entry<String, String>, XMLStreamException> getPropertyWriter() {
            return this.writer;
        }
    }

    static class PropertiesAttributeMarshaller extends AttributeMarshaller {
        private final ExceptionBiConsumer<XMLStreamWriter, Map.Entry<String, String>, XMLStreamException> entryWriter;

        PropertiesAttributeMarshaller(ExceptionBiConsumer<XMLStreamWriter, Map.Entry<String, String>, XMLStreamException> entryWriter) {
            this.entryWriter = entryWriter;
        }

        @Override
        public void marshallAsElement(AttributeDefinition attribute, ModelNode model, boolean marshallDefault, XMLStreamWriter writer) throws XMLStreamException {
            if (model.hasDefined(attribute.getName())) {
                for (Property property : model.get(attribute.getName()).asPropertyList()) {
                    writer.writeStartElement(attribute.getXmlName());
                    this.entryWriter.accept(writer, Map.entry(property.getName(), property.getValue().asString()));
                    writer.writeEndElement();
                }
            }
        }

        @Override
        public boolean isMarshallableAsElement() {
            return true;
        }
    }

    static class PropertiesAttributeParser extends AttributeParser {
        private final ExceptionFunction<XMLExtendedStreamReader, Map.Entry<String, String>, XMLStreamException> entryReader;

        PropertiesAttributeParser(ExceptionFunction<XMLExtendedStreamReader, Map.Entry<String, String>, XMLStreamException> entryReader) {
            this.entryReader = entryReader;
        }

        @Override
        public boolean isParseAsElement() {
            return true;
        }

        @Override
        public XMLCardinality getCardinality(AttributeDefinition attribute) {
            return attribute.isRequired() ? XMLCardinality.Unbounded.REQUIRED : XMLCardinality.Unbounded.OPTIONAL;
        }

        @Override
        public void parseElement(AttributeDefinition attribute, XMLExtendedStreamReader reader, ModelNode operation) throws XMLStreamException {
            assert attribute instanceof MapAttributeDefinition;
            Map.Entry<String, String> entry = this.entryReader.apply(reader);
            ((MapAttributeDefinition) attribute).parseAndAddParameterElement(entry.getKey(), entry.getValue(), operation, reader);
        }
    }

    /**
     * Build a properties attribute definition.
     * By default, properties will be parsed/marshalled as a repeating XML element of the form:
     * <pre><property name="..." value="..."/></pre>
     * where the element local name may be overridden via {@link Builder#setXmlName(String)}.
     * Alternate persistence may be specified via {@link Builder#setPropertyPersistence(PropertyPersistence)}.
     */
    public static class Builder extends MapAttributeDefinition.Builder<Builder, PropertiesAttributeDefinition> {

        /**
         * Creates a new properties attribute definition builder named "{@value ModelDescriptionConstants#PROPERTIES}".
         */
        public Builder() {
            this(ModelDescriptionConstants.PROPERTIES);
        }

        /**
         * Creates a new properties attribute definition builder using the specified name.
         */
        public Builder(String name) {
            super(name);
            this.setXmlName(Element.PROPERTY.getLocalName());
            this.setRequired(false);
            this.setAllowExpression(true);
            this.setAllowNullElement(false);
            this.setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES);
            this.setPropertyPersistence(new PropertyValueAttributePersistence(Attribute.NAME.getLocalName(), Attribute.VALUE.getLocalName()));
        }

        public Builder(String name, PropertiesAttributeDefinition basis) {
            super(name, basis);
        }

        public Builder setPropertyPersistence(PropertyPersistence persistence) {
            super.setAttributeMarshaller(new PropertiesAttributeMarshaller(persistence.getPropertyWriter()));
            super.setAttributeParser(new PropertiesAttributeParser(persistence.getPropertyReader()));
            return this;
        }

        @Override
        public Builder setAttributeMarshaller(AttributeMarshaller marshaller) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Builder setAttributeParser(AttributeParser parser) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PropertiesAttributeDefinition build() {
            if (this.elementValidator == null) {
                this.elementValidator = new ModelTypeValidator(ModelType.STRING);
            }
            return new PropertiesAttributeDefinition(this);
        }
    }
}
