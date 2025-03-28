/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.FeatureRegistry;
import org.jboss.as.version.Stability;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test validating read/write semantics of {@link XMLAttribute}.
 */
public class XMLAttributeTestCase implements FeatureRegistry, QNameResolver {
    private static final String NAMESPACE_URI = "wildfly:test:1.0";
    private final XMLComponentFactory<Map<Attribute, String>, Map<Attribute, String>> factory = XMLComponentFactory.newInstance(this, QName::new);

    @Override
    public QName resolve(String localName) {
        return new QName(NAMESPACE_URI, localName);
    }

    @Override
    public Stability getStability() {
        return Stability.COMMUNITY;
    }

    enum Attribute {
        REQUIRED(null),
        OPTIONAL(null),
        DEFAULT("default-value"),
        OPTIONAL_FIXED("optional-fixed-value"),
        REQUIRED_FIXED("required-fixed-value"),
        PROHIBITED(null),
        EXPERIMENTAL(null),
        ;
        private final String defaultValue;

        Attribute(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        String getDefaultValue() {
            return this.defaultValue;
        }
    }

    @Test
    public void testAttributes() throws XMLStreamException {
        XMLElement<Map<Attribute, String>, Map<Attribute, String>> containerElement = this.factory.element(this.resolve("element"))
                .addAttribute(this.factory.attribute(this.resolve("required")).withUsage(XMLAttribute.Use.REQUIRED).withConsumer((map, value) -> map.put(Attribute.REQUIRED, value)).withFormatter(map -> map.get(Attribute.REQUIRED)).build())
                .addAttribute(this.factory.attribute(this.resolve("optional")).withConsumer((map, value) -> map.put(Attribute.OPTIONAL, value)).withFormatter(map -> map.get(Attribute.OPTIONAL)).build())
                .addAttribute(this.factory.attribute(this.resolve("default")).withConsumer((map, value) -> map.put(Attribute.DEFAULT, value)).withDefaultValue(Attribute.DEFAULT.getDefaultValue()).withFormatter(map -> map.get(Attribute.DEFAULT)).build())
                .addAttribute(this.factory.attribute(this.resolve("optional-fixed")).withConsumer((map, value) -> map.put(Attribute.OPTIONAL_FIXED, value)).withFixedValue(Attribute.OPTIONAL_FIXED.getDefaultValue()).withFormatter(map -> map.get(Attribute.OPTIONAL_FIXED)).build())
                .addAttribute(this.factory.attribute(this.resolve("required-fixed")).withUsage(XMLAttribute.Use.REQUIRED).withConsumer((map, value) -> map.put(Attribute.REQUIRED_FIXED, value)).withFixedValue(Attribute.REQUIRED_FIXED.getDefaultValue()).withFormatter(map -> map.get(Attribute.REQUIRED_FIXED)).build())
                .addAttribute(this.factory.attribute(this.resolve("prohibited")).withUsage(XMLAttribute.Use.PROHIBITED).withConsumer((map, value) -> map.put(Attribute.PROHIBITED, value)).withFormatter(map -> map.get(Attribute.PROHIBITED)).build())
                .addAttribute(this.factory.attribute(this.resolve("experimental"), Stability.EXPERIMENTAL).withConsumer((map, value) -> map.put(Attribute.EXPERIMENTAL, value)).build())
                .build();

        try (XMLElementTester<Map<Attribute, String>, Map<Attribute, String>> tester = XMLElementTester.of(containerElement, () -> new EnumMap<>(Attribute.class))) {
            // Positive tests

            // Verify minimal attributes present
            String xml = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?><element xmlns=\"%s\" required=\"foo\" required-fixed=\"%s\"/>", NAMESPACE_URI, Attribute.REQUIRED_FIXED.getDefaultValue());
            Map<Attribute, String> result = tester.readElement(xml);

            Assert.assertEquals(result.toString(), 4, result.size());
            Assert.assertEquals(result.toString(), "foo", result.get(Attribute.REQUIRED));
            Assert.assertEquals(result.toString(), Attribute.DEFAULT.getDefaultValue(), result.get(Attribute.DEFAULT));
            Assert.assertEquals(result.toString(), Attribute.OPTIONAL_FIXED.getDefaultValue(), result.get(Attribute.OPTIONAL_FIXED));
            Assert.assertEquals(result.toString(), Attribute.REQUIRED_FIXED.getDefaultValue(), result.get(Attribute.REQUIRED_FIXED));

            assertXMLEquals(xml, tester.writeElement(result));

            // Verify maximal attributes present, overriding any default values
            xml = String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?><element xmlns=\"%s\" default=\"baz\" optional=\"bar\" optional-fixed=\"%s\" required=\"foo\" required-fixed=\"%s\"/>", NAMESPACE_URI, Attribute.OPTIONAL_FIXED.getDefaultValue(), Attribute.REQUIRED_FIXED.getDefaultValue());
            result = tester.readElement(xml);

            Assert.assertEquals(result.toString(), 5, result.size());
            Assert.assertEquals(result.toString(), "foo", result.get(Attribute.REQUIRED));
            Assert.assertEquals(result.toString(), "bar", result.get(Attribute.OPTIONAL));
            Assert.assertEquals(result.toString(), "baz", result.get(Attribute.DEFAULT));
            Assert.assertEquals(result.toString(), "optional-fixed-value", result.get(Attribute.OPTIONAL_FIXED));
            Assert.assertEquals(result.toString(), "required-fixed-value", result.get(Attribute.REQUIRED_FIXED));

            // Optional fixed values will not appear in output
            assertXMLEquals(String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?><element xmlns=\"%s\" default=\"baz\" optional=\"bar\" required=\"foo\" required-fixed=\"%s\"/>", NAMESPACE_URI, Attribute.REQUIRED_FIXED.getDefaultValue()), tester.writeElement(result));

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
        return xml.replaceAll(Pattern.quote(">") + "\\s+" + Pattern.quote("<"), "><");
    }
}
