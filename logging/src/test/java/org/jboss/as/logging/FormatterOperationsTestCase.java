/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.logging.formatters.PatternFormatterResourceDefinition;
import org.jboss.as.logging.formatters.StructuredFormatterResourceDefinition;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.SubsystemOperations;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.core.logmanager.WildFlyLogContextSelector;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class FormatterOperationsTestCase extends AbstractOperationsTestCase {

    private static final Map<String, String> TEST_KEY_OVERRIDES = new LinkedHashMap<>();
    private static final Map<String, String> EXPECTED_META_DATA = new LinkedHashMap<>();

    private KernelServices kernelServices;

    @BeforeClass
    public static void setup() {
        for (AttributeDefinition attribute : StructuredFormatterResourceDefinition.KEY_OVERRIDES.getValueTypes()) {
            final String key = attribute.getName();
            TEST_KEY_OVERRIDES.put(key, convertKey(key));
        }
        EXPECTED_META_DATA.put("key1", "value1");
        EXPECTED_META_DATA.put("key2", "value2");
        EXPECTED_META_DATA.put("key3", null);
    }

    @Before
    public void bootKernelServices() throws Exception {
        kernelServices = boot();
    }

    @After
    public void shutdown() {
        if (kernelServices != null) {
            kernelServices.shutdown();
        }
    }

    @After
    @Override
    public void clearLogContext() throws Exception {
        super.clearLogContext();
        final WildFlyLogContextSelector contextSelector = WildFlyLogContextSelector.getContextSelector();
        if (contextSelector.profileContextExists(PROFILE)) {
            contextSelector.getProfileContext(PROFILE).close();
            contextSelector.removeProfileContext(PROFILE);
        }
    }

    @Override
    protected void standardSubsystemTest(final String configId) {
        // do nothing as this is not a subsystem parsing test
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("/empty-subsystem.xml");
    }

    @Test
    public void testDefaultOperations() throws Exception {
        testPatternFormatter(kernelServices, null);
        testPatternFormatter(kernelServices, PROFILE);
    }

    @Test
    public void testJsonFormatterOperations() throws Exception {
        testJsonFormatter(kernelServices, null);
        testJsonFormatter(kernelServices, PROFILE);
    }

    @Test
    public void testXmlFormatterOperations() throws Exception {
        testXmlFormatter(kernelServices, null);
        testXmlFormatter(kernelServices, PROFILE);
    }

    private void testPatternFormatter(final KernelServices kernelServices, final String profileName) {
        final ModelNode address = createPatternFormatterAddress(profileName, "PATTERN").toModelNode();

        // Add the pattern formatter
        final ModelNode addOp = SubsystemOperations.createAddOperation(address);
        executeOperation(kernelServices, addOp);

        // Write each attribute and check the value
        testWrite(kernelServices, address, PatternFormatterResourceDefinition.PATTERN, "[test] %d{HH:mm:ss,SSS} %-5p [%c] %s%e%n");
        testWrite(kernelServices, address, PatternFormatterResourceDefinition.COLOR_MAP, "info:blue,warn:yellow,error:red,debug:cyan");

        // Undefine attributes
        testUndefine(kernelServices, address, PatternFormatterResourceDefinition.PATTERN);
        testUndefine(kernelServices, address, PatternFormatterResourceDefinition.COLOR_MAP);

        // Clean-up
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(address));
        verifyRemoved(kernelServices, address);
    }

    private void testJsonFormatter(final KernelServices kernelServices, final String profileName) {
        final ModelNode address = createAddress(profileName, "json-formatter", "JSON").toModelNode();
        testStructuredFormatter(kernelServices, address);
    }

    private void testXmlFormatter(final KernelServices kernelServices, final String profileName) {
        final ModelNode address = createAddress(profileName, "xml-formatter", "XML").toModelNode();
        testStructuredFormatter(kernelServices, address);
        // Test additional attributes
        testWrite(kernelServices, address, "print-namespace", true);
        testWrite(kernelServices, address, "namespace-uri", "urn:jboss:as:logging:test:1.0");

        testUndefine(kernelServices, address, "print-namespace");
        testUndefine(kernelServices, address, "namespace-uri");
    }



    private void testStructuredFormatter(final KernelServices kernelServices, final ModelNode address) {

        final String dateFormat = "yyyy-MM-dd'T'HH:mm:ssSSSZ";

        // Add the formatter
        final ModelNode op = SubsystemOperations.createAddOperation(address);
        op.get("date-format").set(dateFormat);
        op.get("exception-output-type").set("formatted");
        final ModelNode keyOverrides = op.get("key-overrides").setEmptyObject();
        for (String key : TEST_KEY_OVERRIDES.keySet()) {
            keyOverrides.get(key).set(TEST_KEY_OVERRIDES.get(key));
        }

        final ModelNode metaData = op.get("meta-data");
        for (String key : EXPECTED_META_DATA.keySet()) {
            final String value = EXPECTED_META_DATA.get(key);
            if (value == null) {
                metaData.get(key);
            } else {
                metaData.get(key).set(value);
            }
        }
        op.get("pretty-print").set(true);
        op.get("print-details").set(true);
        op.get("record-delimiter").set("\r\n");
        op.get("zone-id").set("GMT");
        executeOperation(kernelServices, op);

        // Verify we've been added with the expected values
        validateValue(kernelServices, address, "date-format", dateFormat);
        validateValue(kernelServices, address, "exception-output-type", "formatted");
        validateValue(kernelServices, address, "key-overrides", TEST_KEY_OVERRIDES);
        validateValue(kernelServices, address, "meta-data", EXPECTED_META_DATA);
        validateValue(kernelServices, address, "pretty-print", true);
        validateValue(kernelServices, address, "print-details", true);
        validateValue(kernelServices, address, "record-delimiter", "\r\n");
        validateValue(kernelServices, address, "zone-id", "GMT");

        // Remove the formatter
        executeOperation(kernelServices, SubsystemOperations.createRemoveOperation(address));
        verifyRemoved(kernelServices, address);

        // Re-add the formatter with no parameters and test writes and undefines
        executeOperation(kernelServices, Operations.createAddOperation(address));

        testWrite(kernelServices, address, "date-format", dateFormat);
        testWrite(kernelServices, address, "exception-output-type", "detailed-and-formatted");
        // This will require a reload, but we should be okay since we're only testing the model
        testWrite(kernelServices, address, "key-overrides", TEST_KEY_OVERRIDES);
        testWrite(kernelServices, address, "meta-data", EXPECTED_META_DATA);
        testWrite(kernelServices, address, "pretty-print", true);
        testWrite(kernelServices, address, "print-details", true);
        testWrite(kernelServices, address, "record-delimiter", "\r\n");
        testWrite(kernelServices, address, "zone-id", "GMT");

        testUndefine(kernelServices, address, "date-format");
        testUndefine(kernelServices, address, "exception-output-type");
        testUndefine(kernelServices, address, "key-overrides");
        testUndefine(kernelServices, address, "meta-data");
        testUndefine(kernelServices, address, "pretty-print");
        testUndefine(kernelServices, address, "print-details");
        testUndefine(kernelServices, address, "record-delimiter");
        testUndefine(kernelServices, address, "zone-id");

    }

    private void testWrite(final KernelServices kernelServices, final ModelNode address, final String attributeName, final String value) {
        final ModelNode writeOp = SubsystemOperations.createWriteAttributeOperation(address, attributeName, value);
        executeOperation(kernelServices, writeOp);
        validateValue(kernelServices, address, attributeName, value);
    }

    @SuppressWarnings("SameParameterValue")
    private void testWrite(final KernelServices kernelServices, final ModelNode address, final String attributeName, final boolean value) {
        final ModelNode writeOp = SubsystemOperations.createWriteAttributeOperation(address, attributeName, value);
        executeOperation(kernelServices, writeOp);
        validateValue(kernelServices, address, attributeName, value);
    }

    private void testWrite(final KernelServices kernelServices, final ModelNode address, final String attributeName, final Map<String, String> value) {
        final ModelNode modelValue = new ModelNode().setEmptyObject();
        for (Map.Entry<String, String> entry : value.entrySet()) {
            final String mapValue = entry.getValue();
            if (mapValue == null) {
                modelValue.get(entry.getKey());
            } else {
                modelValue.get(entry.getKey()).set(mapValue);
            }
        }
        final ModelNode writeOp = SubsystemOperations.createWriteAttributeOperation(address, attributeName, modelValue);
        executeOperation(kernelServices, writeOp);
        validateValue(kernelServices, address, attributeName, value);
    }

    private void validateValue(final KernelServices kernelServices, final ModelNode address, final String attributeName, final String expectedValue) {
        final ModelNode value = executeOperation(kernelServices, SubsystemOperations.createReadAttributeOperation(address, attributeName));
        Assert.assertEquals(expectedValue, SubsystemOperations.readResult(value).asString());
    }

    private void validateValue(final KernelServices kernelServices, final ModelNode address, final String attributeName, final boolean expectedValue) {
        final ModelNode value = executeOperation(kernelServices, SubsystemOperations.createReadAttributeOperation(address, attributeName));
        Assert.assertEquals(expectedValue, SubsystemOperations.readResult(value).asBoolean());
    }

    private void validateValue(final KernelServices kernelServices, final ModelNode address, final String attributeName, final Map<String, String> expectedValues) {
        final ModelNode value = executeOperation(kernelServices, SubsystemOperations.createReadAttributeOperation(address, attributeName));
        Assert.assertEquals(ModelType.OBJECT, value.getType());
        final Map<String, String> remainingKeys = new LinkedHashMap<>(expectedValues);
        for (Property property : SubsystemOperations.readResult(value).asPropertyList()) {
            final String key = property.getName();
            final String expectedValue = remainingKeys.remove(key);
            if (expectedValue == null) {
                Assert.assertFalse(property.getValue().isDefined());
            } else {
                Assert.assertEquals(expectedValue, property.getValue().asString());
            }
        }

        Assert.assertTrue(String.format("Missing keys in model:%nKeys: %s%nModel%s%n", remainingKeys, SubsystemOperations.readResult(value)), remainingKeys.isEmpty());
    }

    private static String convertKey(final String key) {
        final char[] chars = key.toCharArray();
        final StringBuilder result = new StringBuilder(chars.length);
        for (int i = 0; i < chars.length; i++) {
            final char c = chars[i];
            if (c == '-') {
                // We're making an assumption no characters start or end with a -
                result.append(Character.toUpperCase(chars[++i]));
            } else {
                result.append(c);
            }
        }
        return result.append("Override").toString();
    }
}
