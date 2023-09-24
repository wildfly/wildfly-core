/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.logging.formatters;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpStatus;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.test.integration.logging.AbstractLoggingTestCase;
import org.jboss.as.test.integration.logging.LoggingServiceActivator;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.xml.sax.InputSource;

/**
 * Tests output from the XML formatter to ensure that integration between the subsystem and the log manager is correct.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildFlyRunner.class)
public class XmlFormatterTestCase extends AbstractLoggingTestCase {

    private static final String FILE_NAME = "xml-file-handler.log";
    private static final String XML_HANDLER_NAME = "xmlFileHandler";
    private static final String XML_FORMATTER_NAME = "xml";
    private static final ModelNode HANDLER_ADDRESS = createAddress("file-handler", XML_HANDLER_NAME);
    private static final ModelNode FORMATTER_ADDRESS = createAddress("xml-formatter", XML_FORMATTER_NAME);
    private static final ModelNode LOGGER_ADDRESS = createAddress("logger", LoggingServiceActivator.LOGGER.getName());

    private Path logFile = null;

    @BeforeClass
    public static void deploy() throws Exception {
        deploy(createDeployment(), DEPLOYMENT_NAME);
    }

    @AfterClass
    public static void undeploy() throws Exception {
        undeploy(DEPLOYMENT_NAME);
    }

    @Before
    public void setLogFile() {
        if (logFile == null) {
            logFile = getAbsoluteLogFilePath(FILE_NAME);
        }
    }

    @After
    public void remove() throws Exception {

        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        // Remove the logger
        builder.addStep(Operations.createRemoveOperation(LOGGER_ADDRESS));

        // Remove the formatter
        builder.addStep(Operations.createRemoveOperation(FORMATTER_ADDRESS));

        // Remove the custom handler
        builder.addStep(Operations.createRemoveOperation(HANDLER_ADDRESS));

        executeOperation(builder.build());

        if (logFile != null) Files.deleteIfExists(logFile);
    }

    @Test
    public void testKeyOverrides() throws Exception {
        final Map<String, String> keyOverrides = new HashMap<>();
        keyOverrides.put("timestamp", "dateTime");
        keyOverrides.put("sequence", "seq");
        final Map<String, String> metaData = new LinkedHashMap<>();
        metaData.put("test-key-1", "test-value-1");
        metaData.put("key-no-value", null);
        // Configure the subsystem
        configure(keyOverrides, metaData, true);

        final String msg = "Logging test: XmlFormatterTestCase.defaultLoggingTest";
        final Map<String, String> params = new LinkedHashMap<>();
        // Indicate we need an exception logged
        params.put(LoggingServiceActivator.LOG_EXCEPTION_KEY, "true");
        // Add an NDC value
        params.put(LoggingServiceActivator.NDC_KEY, "test.ndc.value");
        // Add some map entries for MDC values
        params.put("mdcKey1", "mdcValue1");
        params.put("mdcKey2", "mdcValue2");

        final List<String> expectedKeys = createDefaultKeys();
        expectedKeys.remove("timestamp");
        expectedKeys.remove("sequence");
        expectedKeys.addAll(keyOverrides.values());
        expectedKeys.add("metaData");
        expectedKeys.add("exception");
        expectedKeys.add("stackTrace");
        expectedKeys.add("sourceFileName");
        expectedKeys.add("sourceMethodName");
        expectedKeys.add("sourceClassName");
        expectedKeys.add("sourceLineNumber");
        expectedKeys.add("sourceModuleVersion");
        expectedKeys.add("sourceModuleName");

        final int statusCode = getResponse(msg, params);
        Assert.assertEquals("Invalid response statusCode: " + statusCode, HttpStatus.SC_OK, statusCode);

        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder builder = factory.newDocumentBuilder();

        // Validate each line
        for (String s : readLogLines()) {
            final Document doc = builder.parse(new InputSource(new StringReader(s)));

            validateDefault(doc, expectedKeys, msg);

            // Timestamp should have been renamed to dateTime
            Assert.assertEquals("Found timestamp entry in " + s, 0, doc.getElementsByTagName("timestamp").getLength());

            // Sequence should have been renamed to seq
            Assert.assertEquals("Found sequence entry in " + s, 0, doc.getElementsByTagName("sequence").getLength());

            // Validate MDC
            final NodeList mdcNodes = doc.getElementsByTagName("mdc");
            Assert.assertEquals(1, mdcNodes.getLength());
            final Node mdcItem = mdcNodes.item(0);
            final NodeList mdcChildren = mdcItem.getChildNodes();
            Assert.assertEquals(2, mdcChildren.getLength());
            final Node mdc1 = mdcChildren.item(0);
            Assert.assertEquals("mdcKey1", mdc1.getNodeName());
            Assert.assertEquals("mdcValue1", mdc1.getTextContent());
            final Node mdc2 = mdcChildren.item(1);
            Assert.assertEquals("mdcKey2", mdc2.getNodeName());
            Assert.assertEquals("mdcValue2", mdc2.getTextContent());

            // Validate the meta-data
            final NodeList metaDataNodes = doc.getElementsByTagName("metaData");
            Assert.assertEquals(2, metaDataNodes.getLength());
            Assert.assertEquals("test-key-1", metaDataNodes.item(0).getAttributes().getNamedItem("key").getTextContent());
            Assert.assertEquals("test-value-1", metaDataNodes.item(0).getTextContent());
            Assert.assertEquals("key-no-value", metaDataNodes.item(1).getAttributes().getNamedItem("key").getTextContent());
            Assert.assertEquals("", metaDataNodes.item(1).getTextContent());

            validateStackTrace(doc, true, true);
        }
    }

    @Test
    public void testNoExceptions() throws Exception {
        configure(Collections.emptyMap(), Collections.emptyMap(), false);
        final String msg = "Logging test: XmlFormatterTestCase.testNoExceptions";
        int statusCode = getResponse(msg, Collections.singletonMap(LoggingServiceActivator.LOG_EXCEPTION_KEY, "false"));
        Assert.assertEquals("Invalid response statusCode: " + statusCode, HttpStatus.SC_OK, statusCode);

        final List<String> expectedKeys = createDefaultKeys();

        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder builder = factory.newDocumentBuilder();

        for (String s : readLogLines()) {
            if (s.trim().isEmpty()) continue;
            final Document doc = builder.parse(new InputSource(new StringReader(s)));
            validateDefault(doc, expectedKeys, msg);
            validateStackTrace(doc, false, false);
        }
    }

    @Test
    public void testFormattedException() throws Exception {
        configure(Collections.emptyMap(), Collections.emptyMap(), false);

        // Change the exception-output-type
        executeOperation(Operations.createWriteAttributeOperation(FORMATTER_ADDRESS, "exception-output-type", "formatted"));

        final String msg = "Logging test: XmlFormatterTestCase.testNoExceptions";
        int statusCode = getResponse(msg, Collections.singletonMap(LoggingServiceActivator.LOG_EXCEPTION_KEY, "true"));
        Assert.assertEquals("Invalid response statusCode: " + statusCode, HttpStatus.SC_OK, statusCode);

        final List<String> expectedKeys = createDefaultKeys();
        expectedKeys.add("stackTrace");

        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder builder = factory.newDocumentBuilder();

        for (String s : readLogLines()) {
            if (s.trim().isEmpty()) continue;
            final Document doc = builder.parse(new InputSource(new StringReader(s)));
            validateDefault(doc, expectedKeys, msg);
            validateStackTrace(doc, true, false);
        }
    }

    @Test
    public void testStructuredException() throws Exception {
        configure(Collections.emptyMap(), Collections.emptyMap(), false);

        // Change the exception-output-type
        executeOperation(Operations.createWriteAttributeOperation(FORMATTER_ADDRESS, "exception-output-type", "detailed"));

        final String msg = "Logging test: XmlFormatterTestCase.testNoExceptions";
        int statusCode = getResponse(msg, Collections.singletonMap(LoggingServiceActivator.LOG_EXCEPTION_KEY, "true"));
        Assert.assertEquals("Invalid response statusCode: " + statusCode, HttpStatus.SC_OK, statusCode);

        final List<String> expectedKeys = createDefaultKeys();
        expectedKeys.add("exception");

        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder builder = factory.newDocumentBuilder();

        for (String s : readLogLines()) {
            if (s.trim().isEmpty()) continue;
            final Document doc = builder.parse(new InputSource(new StringReader(s)));
            validateDefault(doc, expectedKeys, msg);
            validateStackTrace(doc, false, true);
        }
    }

    @Test
    public void testDateFormat() throws Exception {
        configure(Collections.emptyMap(), Collections.emptyMap(), false);

        final String dateFormat = "yyyy-MM-dd'T'HH:mm:ssSSSZ";
        final String timezone = "GMT";

        // Change the date format and time zone
        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();
        builder.addStep(Operations.createWriteAttributeOperation(FORMATTER_ADDRESS, "date-format", dateFormat));
        builder.addStep(Operations.createWriteAttributeOperation(FORMATTER_ADDRESS, "zone-id", timezone));
        executeOperation(builder.build());

        final String msg = "Logging test: XmlFormatterTestCase.testNoExceptions";
        int statusCode = getResponse(msg, Collections.singletonMap(LoggingServiceActivator.LOG_EXCEPTION_KEY, "false"));
        Assert.assertEquals("Invalid response statusCode: " + statusCode, HttpStatus.SC_OK, statusCode);

        final List<String> expectedKeys = createDefaultKeys();

        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder documentBuilder = factory.newDocumentBuilder();

        for (String s : Files.readAllLines(logFile, StandardCharsets.UTF_8)) {
            if (s.trim().isEmpty()) continue;
            final Document doc = documentBuilder.parse(new InputSource(new StringReader(s)));

            validateDefault(doc, expectedKeys, msg);
            validateStackTrace(doc, false, false);

            // Validate the date format is correct. We don't want to validate the specific date, only that it's
            // parsable.
            final NodeList timestampNode = doc.getElementsByTagName("timestamp");
            Assert.assertEquals(1, timestampNode.getLength());
            final String xmlDate = timestampNode.item(0).getTextContent();
            // If the date is not parsable an exception should be thrown
            try {
                DateTimeFormatter.ofPattern(dateFormat, Locale.ROOT).withZone(ZoneId.of(timezone)).parse(xmlDate);
            } catch (Exception e) {
                Assert.fail(String.format("Failed to parse %s with pattern %s and zone %s: %s", xmlDate, dateFormat,
                        timezone, e.getMessage()));
            }
        }
    }

    private Collection<String> readLogLines() throws IOException {
        final Collection<String> result = new ArrayList<>();
        final StringBuilder sb = new StringBuilder();
        for (String line : Files.readAllLines(logFile, StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty()) continue;
            sb.append(line);
            if (line.endsWith("</record>")) {
                result.add(sb.toString());
                sb.setLength(0);
            }
        }
        return result;
    }

    private static void validateDefault(final Document doc, final Collection<String> expectedKeys, final String expectedMessage) {
        final Set<String> remainingKeys = new HashSet<>(expectedKeys);

        String loggerClassName = null;
        String loggerName = null;
        String level = null;
        String message = null;

        // Check all the expected keys
        final Iterator<String> iterator = remainingKeys.iterator();
        while (iterator.hasNext()) {
            final String key = iterator.next();
            final NodeList children = doc.getElementsByTagName(key);
            Assert.assertTrue(String.format("Key %s was not found in the document: %s", key, doc), children.getLength() > 0);
            final Node child = children.item(0);
            if ("loggerClassName".equals(child.getNodeName())) {
                loggerClassName = child.getTextContent();
            } else if ("loggerName".equals(child.getNodeName())) {
                loggerName = child.getTextContent();
            } else if ("level".equals(child.getNodeName())) {
                level = child.getTextContent();
            } else if ("message".equals(child.getNodeName())) {
                message = child.getTextContent();
            }
            iterator.remove();
        }

        // Should have no more remaining keys
        Assert.assertTrue("There are remaining keys that were not validated: " + remainingKeys, remainingKeys.isEmpty());

        Assert.assertEquals("org.jboss.logging.Logger", loggerClassName);
        Assert.assertEquals(LoggingServiceActivator.LOGGER.getName(), loggerName);
        Assert.assertTrue("Invalid level found in " + level, isValidLevel(level));
        Assert.assertEquals(expectedMessage, message);
    }


    private static void validateStackTrace(final Document doc, final boolean validateFormatted, final boolean validateStructured) {
        if (validateFormatted) {
            Assert.assertTrue(doc.getElementsByTagName("stackTrace").getLength() > 0);
        } else {
            Assert.assertEquals(0, doc.getElementsByTagName("stackTrace").getLength());
        }
        if (validateStructured) {
            final NodeList exceptions = doc.getElementsByTagName("exception");
            Assert.assertTrue(exceptions.getLength() > 0);
            validateStackTrace(exceptions.item(0));
        } else {
            Assert.assertEquals(0, doc.getElementsByTagName("exception").getLength());
        }
    }

    private static void validateStackTrace(final Node element) {
        Assert.assertEquals(Node.ELEMENT_NODE, element.getNodeType());
        Assert.assertNotNull(element.getAttributes().getNamedItem("refId"));
        final NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            final String name = child.getNodeName();
            if ("causedBy".equals(name) || "suppressed".equals(name)) {
                validateStackTrace(child.getFirstChild());
            }
        }
    }

    private static boolean isValidLevel(final String level) {
        Assert.assertNotNull("A null level was found", level);
        for (Logger.Level l : LoggingServiceActivator.LOG_LEVELS) {
            if (l.name().equals(level)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> createDefaultKeys() {
        final List<String> expectedKeys = new ArrayList<>();
        expectedKeys.add("timestamp");
        expectedKeys.add("sequence");
        expectedKeys.add("loggerClassName");
        expectedKeys.add("loggerName");
        expectedKeys.add("level");
        expectedKeys.add("message");
        expectedKeys.add("threadName");
        expectedKeys.add("threadId");
        expectedKeys.add("mdc");
        expectedKeys.add("ndc");
        expectedKeys.add("hostName");
        expectedKeys.add("processName");
        expectedKeys.add("processId");
        return expectedKeys;
    }

    private void configure(final Map<String, String> keyOverrides, final Map<String, String> metaData,
                           final boolean printDetails) throws IOException {

        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        // Create a XML formatter
        ModelNode op = Operations.createAddOperation(FORMATTER_ADDRESS);

        final ModelNode keyOverridesNode = op.get("key-overrides").setEmptyObject();
        for (String key : keyOverrides.keySet()) {
            final String value = keyOverrides.get(key);
            if (value == null) {
                keyOverridesNode.get(key);
            } else {
                keyOverridesNode.get(key).set(value);
            }
        }

        final ModelNode metaDataNode = op.get("meta-data").setEmptyObject();
        for (String key : metaData.keySet()) {
            final String value = metaData.get(key);
            if (value == null) {
                metaDataNode.get(key);
            } else {
                metaDataNode.get(key).set(value);
            }
        }

        op.get("exception-output-type").set("detailed-and-formatted");
        op.get("print-details").set(printDetails);
        // This should always be false so that the each line will be a separate entry since we're writing to a file
        op.get("pretty-print").set(false);
        builder.addStep(op);

        // Create the handler
        op = Operations.createAddOperation(HANDLER_ADDRESS);
        final ModelNode fileNode = op.get("file").setEmptyObject();
        fileNode.get("relative-to").set("jboss.server.log.dir");
        fileNode.get("path").set(logFile.getFileName().toString());
        op.get("autoFlush").set(true);
        op.get("append").set(false);
        op.get("named-formatter").set(XML_FORMATTER_NAME);
        builder.addStep(op);

        // Add the handler to the root-logger
        op = Operations.createAddOperation(LOGGER_ADDRESS);
        op.get("handlers").setEmptyList().add(XML_HANDLER_NAME);
        builder.addStep(op);

        executeOperation(builder.build());
    }
}
