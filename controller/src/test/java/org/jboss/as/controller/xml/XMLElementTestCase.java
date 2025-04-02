/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.FeatureRegistry;
import org.jboss.as.version.Stability;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test validating read/write semantics of an {@link XMLElement} containing attributes and text content.
 */
public class XMLElementTestCase implements FeatureRegistry, QNameResolver {
    private static final String NAMESPACE_URI = "wildfly:test:1.0";
    private final XMLComponentFactory<Map<Attribute, String>, Map<Attribute, String>> factory = XMLComponentFactory.newInstance(this, this);

    @Override
    public QName resolve(String localName) {
        return new QName(NAMESPACE_URI, localName);
    }

    @Override
    public Stability getStability() {
        return Stability.COMMUNITY;
    }

    enum Attribute implements BiConsumer<Map<Attribute, String>, String>, Function<Map<Attribute, String>, String> {
        REQUIRED(null),
        OPTIONAL(null),
        DEFAULT("default-value"),
        OPTIONAL_FIXED("optional-fixed-value"),
        REQUIRED_FIXED("required-fixed-value"),
        PROHIBITED(null),
        EXPERIMENTAL(null),
        CONTENT(null)
        ;
        private final String defaultValue;

        Attribute(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        String getDefaultValue() {
            return this.defaultValue;
        }

        @Override
        public String apply(Map<Attribute, String> map) {
            return map.get(this);
        }

        @Override
        public void accept(Map<Attribute, String> map, String value) {
            map.put(this, value);
        }
    }

    @Test
    public void testAttributes() throws XMLStreamException {
        XMLElement<Map<Attribute, String>, Map<Attribute, String>> containerElement = this.factory.element(this.resolve("element"))
                .addAttribute(this.factory.attribute(this.resolve("required")).withUsage(XMLAttribute.Use.REQUIRED).withConsumer(Attribute.REQUIRED).withFormatter(Attribute.REQUIRED).build())
                .addAttribute(this.factory.attribute(this.resolve("optional")).withConsumer(Attribute.OPTIONAL).withFormatter(Attribute.OPTIONAL).build())
                .addAttribute(this.factory.attribute(this.resolve("default")).withConsumer(Attribute.DEFAULT).withDefaultValue(Attribute.DEFAULT.getDefaultValue()).withFormatter(Attribute.DEFAULT).build())
                .addAttribute(this.factory.attribute(this.resolve("optional-fixed")).withConsumer(Attribute.OPTIONAL_FIXED).withFixedValue(Attribute.OPTIONAL_FIXED.getDefaultValue()).withFormatter(Attribute.OPTIONAL_FIXED).build())
                .addAttribute(this.factory.attribute(this.resolve("required-fixed")).withUsage(XMLAttribute.Use.REQUIRED).withConsumer(Attribute.REQUIRED_FIXED).withFixedValue(Attribute.REQUIRED_FIXED.getDefaultValue()).withFormatter(Attribute.REQUIRED_FIXED).build())
                .addAttribute(this.factory.attribute(this.resolve("prohibited")).withUsage(XMLAttribute.Use.PROHIBITED).withConsumer(Attribute.PROHIBITED).withFormatter(Attribute.PROHIBITED).build())
                .addAttribute(this.factory.attribute(this.resolve("experimental"), Stability.EXPERIMENTAL).withConsumer(Attribute.EXPERIMENTAL).withFormatter(Attribute.EXPERIMENTAL).build())
                .withContent(XMLContent.of(Attribute.CONTENT, Attribute.CONTENT))
                .build();

        try (XMLElementTester<Map<Attribute, String>, Map<Attribute, String>> tester = XMLElementTester.of(containerElement, () -> new EnumMap<>(Attribute.class))) {
            // Positive tests

            // Verify minimal attributes present
            String xml = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?><element xmlns=\"%s\" required=\"foo\" required-fixed=\"%s\"/>", NAMESPACE_URI, Attribute.REQUIRED_FIXED.getDefaultValue());
            Map<Attribute, String> result = tester.readElement(xml);

            Assert.assertEquals(result.toString(), 5, result.size());
            Assert.assertEquals(result.toString(), "foo", result.get(Attribute.REQUIRED));
            Assert.assertEquals(result.toString(), Attribute.DEFAULT.getDefaultValue(), result.get(Attribute.DEFAULT));
            Assert.assertEquals(result.toString(), Attribute.OPTIONAL_FIXED.getDefaultValue(), result.get(Attribute.OPTIONAL_FIXED));
            Assert.assertEquals(result.toString(), Attribute.REQUIRED_FIXED.getDefaultValue(), result.get(Attribute.REQUIRED_FIXED));
            Assert.assertEquals(result.toString(), "", result.get(Attribute.CONTENT));

            assertXMLEquals(xml, tester.writeElement(result));

            // Verify maximal attributes present, overriding any default values
            xml = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?><element xmlns=\"%s\" default=\"baz\" optional=\"bar\" optional-fixed=\"%s\" required=\"foo\" required-fixed=\"%s\">qux</element>", NAMESPACE_URI, Attribute.OPTIONAL_FIXED.getDefaultValue(), Attribute.REQUIRED_FIXED.getDefaultValue());
            result = tester.readElement(xml);

            Assert.assertEquals(result.toString(), 6, result.size());
            Assert.assertEquals(result.toString(), "foo", result.get(Attribute.REQUIRED));
            Assert.assertEquals(result.toString(), "bar", result.get(Attribute.OPTIONAL));
            Assert.assertEquals(result.toString(), "baz", result.get(Attribute.DEFAULT));
            Assert.assertEquals(result.toString(), "optional-fixed-value", result.get(Attribute.OPTIONAL_FIXED));
            Assert.assertEquals(result.toString(), "required-fixed-value", result.get(Attribute.REQUIRED_FIXED));
            Assert.assertEquals(result.toString(), "qux", result.get(Attribute.CONTENT));

            // Optional fixed values will not appear in output
            assertXMLEquals(String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?><element xmlns=\"%s\" default=\"baz\" optional=\"bar\" required=\"foo\" required-fixed=\"%s\">qux</element>", NAMESPACE_URI, Attribute.REQUIRED_FIXED.getDefaultValue()), tester.writeElement(result));

            // Negative tests

            // Missing required attribute
            Assert.assertThrows(XMLStreamException.class, () -> tester.readElement(String.format("<element xmlns=\"%s\"/>", NAMESPACE_URI)));
            Assert.assertThrows(XMLStreamException.class, () -> tester.readElement(String.format("<element xmlns=\"%s\" required=\"foo\"/>", NAMESPACE_URI)));
            Assert.assertThrows(XMLStreamException.class, () -> tester.readElement(String.format("<element xmlns=\"%s\" required-fixed=\"%s\"/>", NAMESPACE_URI, Attribute.REQUIRED_FIXED.getDefaultValue())));

            // Duplicate attribute
            Assert.assertThrows(XMLStreamException.class, () -> tester.readElement(String.format("<element xmlns=\"%s\" optional=\"bar\" optional=\"baz\" required=\"foo\" required-fixed=\"%s\"/>", NAMESPACE_URI, Attribute.REQUIRED_FIXED.getDefaultValue())));

            // Unexpected attribute
            Assert.assertThrows(XMLStreamException.class, () -> tester.readElement(String.format("<element xmlns=\"%s\" required=\"foo\" unexpected=\"foo\" required-fixed=\"%s\"/>", NAMESPACE_URI, Attribute.REQUIRED_FIXED.getDefaultValue())));

            // Impermissible fixed attribute
            Assert.assertThrows(XMLStreamException.class, () -> tester.readElement(String.format("<element xmlns=\"%s\" required=\"foo\" required-fixed=\"bar\"/>", NAMESPACE_URI)));
            Assert.assertThrows(XMLStreamException.class, () -> tester.readElement(String.format("<element xmlns=\"%s\" optional-fixed=\"bar\" required=\"foo\" required-fixed=\"%s\"/>", NAMESPACE_URI, Attribute.REQUIRED_FIXED.getDefaultValue())));

            // Prohibited attribute
            Assert.assertThrows(XMLStreamException.class, () -> tester.readElement(String.format("<element xmlns=\"%s\" prohibited=\"bar\" required=\"foo\" required-fixed=\"%s\"/>", NAMESPACE_URI, Attribute.REQUIRED_FIXED.getDefaultValue())));

            // Experimental attribute
            Assert.assertThrows(XMLStreamException.class, () -> tester.readElement(String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?><element xmlns=\"%s\" experimental=\"bar\" required=\"foo\" required-fixed=\"%s\"/>", NAMESPACE_URI, Attribute.REQUIRED_FIXED.getDefaultValue())));
        }
    }

    private static void assertXMLEquals(String expected, String actual) {
        Assert.assertEquals(actual, trim(expected), trim(actual));
    }

    private static String trim(String xml) {
        // Trim whitespace between elements
        return xml.replaceAll(Pattern.quote(">") + "\\s*(\\S*)\\s*" + Pattern.quote("<"), ">$1<");
    }
}
