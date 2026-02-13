/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.version.Stability;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.wildfly.common.Assert;
import org.wildfly.common.function.Functions;

/**
 * Encapsulates an attribute of an XML element.
 */
public interface XMLAttribute<RC, WC> extends XMLComponent<RC, WC> {

    /**
     * Returns the qualified name of this attribute.
     * @return the qualified name of this attribute.
     */
    QName getName();

    @Override
    XMLAttributeReader<RC> getReader();

    /**
     * Returns the usage of this XML attribute.
     * @return the usage of this XML attribute.
     */
    XMLUsage getUsage();

    /**
     * Enumerates permissible xs:attribute usage.
     */
    enum Use implements XMLUsage {
        OPTIONAL(false, true),
        PROHIBITED(false, false),
        REQUIRED(true, true),
        ;
        private final boolean required;
        private final boolean enabled;

        Use(boolean required, boolean enabled) {
            this.required = required;
            this.enabled = enabled;
        }

        @Override
        public boolean isRequired() {
            return this.required;
        }

        @Override
        public boolean isEnabled() {
            return this.enabled;
        }
    }

    interface Builder<RC, WC> {
        /**
         * Overrides the usage of this attribute
         * @param usage the attribute usage
         * @return a reference to this builder
         */
        Builder<RC, WC> withUsage(XMLUsage usage);

        /**
         * Specifies a default value for this attribute, applied if the attribute is absent from the input.
         * @param defaultValue the default value of this attribute
         * @return a reference to this builder
         */
        Builder<RC, WC> withDefaultValue(String defaultValue);

        /**
         * Specifies a fixed value of this attribute, applied if the attribute is absent from the input.
         * However, if this attribute is present in the input, it must specify this fixed value.
         * @param fixedValue the fixed value of this attribute
         * @return a reference to this builder
         */
        Builder<RC, WC> withFixedValue(String fixedValue);

        /**
         * Specifies a consumer used to apply an attribute value to the read context.
         * @param consumer consumes the attribute value into the read context
         * @return a reference to this builder
         */
        Builder<RC, WC> withConsumer(BiConsumer<RC, String> consumer);

        /**
         * Specifies a function used to format content as an attribute value.
         * @param formatter a function returning the attribute value of the content to be written.
         * @return a reference to this builder
         */
        Builder<RC, WC> withFormatter(Function<WC, String> formatter);

        /**
         * Builds this attribute
         * @return an XML attribute
         */
        XMLAttribute<RC, WC> build();
    }

    static class DefaultBuilder<RC, WC> implements Builder<RC, WC> {
        private final QName name;
        private final Stability stability;
        private volatile BiConsumer<RC, String> consumer = Functions.discardingBiConsumer();
        private volatile Function<WC, String> formatter = Object::toString;
        private volatile XMLUsage usage = Use.OPTIONAL;
        private volatile String defaultValue = null;
        private volatile boolean fixed = false;

        DefaultBuilder(QName name, Stability stability) {
            this.name = name;
            this.stability = stability;
        }

        @Override
        public Builder<RC, WC> withConsumer(BiConsumer<RC, String> consumer) {
            this.consumer = consumer;
            return this;
        }

        @Override
        public Builder<RC, WC> withFormatter(Function<WC, String> formatter) {
            this.formatter = formatter;
            return this;
        }

        @Override
        public Builder<RC, WC> withUsage(XMLUsage usage) {
            this.usage = usage;
            return this;
        }

        @Override
        public Builder<RC, WC> withDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
            this.fixed = false;
            return this;
        }

        @Override
        public Builder<RC, WC> withFixedValue(String fixedValue) {
            Assert.assertNotNull(fixedValue);
            this.withDefaultValue(fixedValue);
            this.fixed = true;
            return this;
        }

        @Override
        public XMLAttribute<RC, WC> build() {
            QName name = this.name;
            XMLUsage usage = this.usage;
            BiConsumer<RC, String> consumer = this.consumer;
            Function<WC, String> formatter = this.formatter;
            String defaultValue = this.defaultValue;
            boolean fixed = this.fixed;
            XMLAttributeReader<RC> reader = new XMLAttributeReader<>() {
                @Override
                public void readAttribute(XMLStreamReader reader, int index, RC context) throws XMLStreamException {
                    String value = reader.getAttributeValue(index);
                    // Detect invalid fixed value
                    if (fixed && !value.equals(defaultValue)) {
                        throw ControllerLogger.ROOT_LOGGER.invalidAttributeValue(value, reader.getAttributeName(index), reader.getLocation());
                    }
                    consumer.accept(context, reader.getAttributeValue(index));
                }

                @Override
                public void whenAbsent(RC context) {
                    if (defaultValue != null) {
                        // Apply default value, if attribute was absent from input
                        consumer.accept(context, defaultValue);
                    }
                }
            };
            XMLContentWriter<WC> writer = new XMLContentWriter<>() {
                @Override
                public void writeContent(XMLExtendedStreamWriter writer, WC content) throws XMLStreamException {
                    String value = formatter.apply(content);
                    if ((value != null) && (usage.isRequired() || !value.equals(defaultValue))) {
                        writer.writeAttribute(name.getLocalPart(), value);
                    }
                }

                @Override
                public boolean isEmpty(WC content) {
                    return formatter.apply(content) == null;
                }
            };
            return new DefaultXMLAttribute<>(this.name, this.usage, reader, writer, this.stability);
        }
    }

    static class DefaultXMLAttribute<RC, WC> implements XMLAttribute<RC, WC> {
        private final QName name;
        private final XMLUsage usage;
        private final XMLAttributeReader<RC> reader;
        private final XMLContentWriter<WC> writer;
        private final Stability stability;

        DefaultXMLAttribute(QName name, XMLUsage usage, XMLAttributeReader<RC> reader, XMLContentWriter<WC> writer, Stability stability) {
            this.name = name;
            this.usage = usage;
            this.reader = reader;
            this.writer = writer;
            this.stability = stability;
        }

        @Override
        public QName getName() {
            return this.name;
        }

        @Override
        public XMLAttributeReader<RC> getReader() {
            return this.reader;
        }

        @Override
        public XMLContentWriter<WC> getWriter() {
            return this.writer;
        }

        @Override
        public XMLUsage getUsage() {
            return this.usage;
        }

        @Override
        public Stability getStability() {
            return this.stability;
        }
    }
}
