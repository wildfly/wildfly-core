/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonValue;

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
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Tests output from the JSON formatter to ensure that integration between the subsystem and the log manager is correct.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(WildFlyRunner.class)
public class JsonFormatterTestCase extends AbstractLoggingTestCase {

    private static final String FILE_NAME = "json-file-handler.log";
    private static final String JSON_HANDLER_NAME = "jsonFileHandler";
    private static final String JSON_FORMATTER_NAME = "json";
    private static final ModelNode HANDLER_ADDRESS = createAddress("file-handler", JSON_HANDLER_NAME);
    private static final ModelNode FORMATTER_ADDRESS = createAddress("json-formatter", JSON_FORMATTER_NAME);
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

        final String msg = "Logging test: JsonFormatterTestCase.defaultLoggingTest";
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
        expectedKeys.addAll(metaData.keySet());
        expectedKeys.add("exception");
        expectedKeys.add("stackTrace");
        expectedKeys.add("sourceFileName");
        expectedKeys.add("sourceMethodName");
        expectedKeys.add("sourceClassName");
        expectedKeys.add("sourceLineNumber");
        expectedKeys.add("sourceModuleVersion");
        expectedKeys.add("sourceModuleName");

        final int statusCode = getResponse(msg, params);
        Assert.assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);

        // Validate each line
        for (String s : Files.readAllLines(logFile, StandardCharsets.UTF_8)) {
            if (s.trim().isEmpty()) continue;
            try (JsonReader reader = Json.createReader(new StringReader(s))) {
                final JsonObject json = reader.readObject();
                validateDefault(json, expectedKeys, msg);

                // Timestamp should have been renamed to dateTime
                Assert.assertNull("Found timestamp entry in " + s, json.get("timestamp"));

                // Sequence should have been renamed to seq
                Assert.assertNull("Found sequence entry in " + s, json.get("sequence"));

                // Validate MDC
                final JsonObject mdcObject = json.getJsonObject("mdc");
                Assert.assertEquals("mdcValue1", mdcObject.getString("mdcKey1"));
                Assert.assertEquals("mdcValue2", mdcObject.getString("mdcKey2"));

                // Validate the meta-data
                Assert.assertEquals("test-value-1", json.getString("test-key-1"));
                Assert.assertEquals("Expected a null type but got " + json.get("key-no-value"),
                        JsonValue.ValueType.NULL, json.get("key-no-value").getValueType());

                validateStackTrace(json, true, true);
            }
        }
    }

    @Test
    public void testNoExceptions() throws Exception {
        configure(Collections.emptyMap(), Collections.emptyMap(), false);
        final String msg = "Logging test: JsonFormatterTestCase.testNoExceptions";
        int statusCode = getResponse(msg, Collections.singletonMap(LoggingServiceActivator.LOG_EXCEPTION_KEY, "false"));
        Assert.assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);

        final List<String> expectedKeys = createDefaultKeys();

        for (String s : Files.readAllLines(logFile, StandardCharsets.UTF_8)) {
            if (s.trim().isEmpty()) continue;
            try (JsonReader reader = Json.createReader(new StringReader(s))) {
                final JsonObject json = reader.readObject();

                validateDefault(json, expectedKeys, msg);
                validateStackTrace(json, false, false);
            }
        }
    }

    @Test
    public void testFormattedException() throws Exception {
        configure(Collections.emptyMap(), Collections.emptyMap(), false);

        // Change the exception-output-type
        executeOperation(Operations.createWriteAttributeOperation(FORMATTER_ADDRESS, "exception-output-type", "formatted"));

        final String msg = "Logging test: JsonFormatterTestCase.testNoExceptions";
        int statusCode = getResponse(msg, Collections.singletonMap(LoggingServiceActivator.LOG_EXCEPTION_KEY, "true"));
        Assert.assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);

        final List<String> expectedKeys = createDefaultKeys();
        expectedKeys.add("stackTrace");

        for (String s : Files.readAllLines(logFile, StandardCharsets.UTF_8)) {
            if (s.trim().isEmpty()) continue;
            try (JsonReader reader = Json.createReader(new StringReader(s))) {
                final JsonObject json = reader.readObject();

                validateDefault(json, expectedKeys, msg);
                validateStackTrace(json, true, false);
            }
        }
    }

    @Test
    public void testStructuredException() throws Exception {
        configure(Collections.emptyMap(), Collections.emptyMap(), false);

        // Change the exception-output-type
        executeOperation(Operations.createWriteAttributeOperation(FORMATTER_ADDRESS, "exception-output-type", "detailed"));

        final String msg = "Logging test: JsonFormatterTestCase.testNoExceptions";
        int statusCode = getResponse(msg, Collections.singletonMap(LoggingServiceActivator.LOG_EXCEPTION_KEY, "true"));
        Assert.assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);

        final List<String> expectedKeys = createDefaultKeys();
        expectedKeys.add("exception");

        for (String s : Files.readAllLines(logFile, StandardCharsets.UTF_8)) {
            if (s.trim().isEmpty()) continue;
            try (JsonReader reader = Json.createReader(new StringReader(s))) {
                final JsonObject json = reader.readObject();

                validateDefault(json, expectedKeys, msg);
                validateStackTrace(json, false, true);
            }
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

        final String msg = "Logging test: JsonFormatterTestCase.testNoExceptions";
        int statusCode = getResponse(msg, Collections.singletonMap(LoggingServiceActivator.LOG_EXCEPTION_KEY, "false"));
        Assert.assertTrue("Invalid response statusCode: " + statusCode, statusCode == HttpStatus.SC_OK);

        final List<String> expectedKeys = createDefaultKeys();

        for (String s : Files.readAllLines(logFile, StandardCharsets.UTF_8)) {
            if (s.trim().isEmpty()) continue;
            try (JsonReader reader = Json.createReader(new StringReader(s))) {
                final JsonObject json = reader.readObject();

                validateDefault(json, expectedKeys, msg);
                validateStackTrace(json, false, false);

                // Validate the date format is correct. We don't want to validate the specific date, only that it's
                // parsable.
                final String jsonDate = json.getString("timestamp");
                // If the date is not parsable an exception should be thrown
                try {
                    DateTimeFormatter.ofPattern(dateFormat, Locale.ROOT).withZone(ZoneId.of(timezone)).parse(jsonDate);
                } catch (Exception e) {
                    Assert.fail(String.format("Failed to parse %s with pattern %s and zone %s: %s", jsonDate, dateFormat,
                            timezone, e.getMessage()));
                }
            }
        }
    }

    private static void validateDefault(final JsonObject json, final Collection<String> expectedKeys, final String expectedMessage) {
        final Set<String> remainingKeys = new HashSet<>(json.keySet());

        // Check all the expected keys
        for (String key : expectedKeys) {
            checkNonNull(json, key);
            Assert.assertTrue("Missing key " + key + " from JSON object: " + json, remainingKeys.remove(key));
        }

        // Should have no more remaining keys
        Assert.assertTrue("There are remaining keys that were not validated: " + remainingKeys, remainingKeys.isEmpty());

        Assert.assertEquals("org.jboss.logging.Logger", json.getString("loggerClassName"));
        Assert.assertEquals(LoggingServiceActivator.LOGGER.getName(), json.getString("loggerName"));
        Assert.assertTrue("Invalid level found in " + json.get("level"), isValidLevel(json.getString("level")));
        Assert.assertEquals(expectedMessage, json.getString("message"));
    }


    private static void validateStackTrace(final JsonObject json, final boolean validateFormatted, final boolean validateStructured) {
        if (validateFormatted) {
            Assert.assertNotNull(json.get("stackTrace"));
        } else {
            Assert.assertNull(json.get("stackTrace"));
        }
        if (validateStructured) {
            validateStackTrace(json.getJsonObject("exception"));
        } else {
            Assert.assertNull(json.get("exception"));
        }
    }

    private static void validateStackTrace(final JsonObject json) {
        checkNonNull(json, "refId");
        checkNonNull(json, "exceptionType");
        checkNonNull(json, "message");
        checkNonNull(json, "frames");
        if (json.get("causedBy") != null) {
            validateStackTrace(json.getJsonObject("causedBy").getJsonObject("exception"));
        }
    }

    private static boolean isValidLevel(final String level) {
        for (Logger.Level l : LoggingServiceActivator.LOG_LEVELS) {
            if (l.name().equals(level)) {
                return true;
            }
        }
        return false;
    }

    private static void checkNonNull(final JsonObject json, final String key) {
        Assert.assertNotNull(String.format("Missing %s in %s", key, json), json.get(key));
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

        // Create a JSON formatter
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
        op.get("named-formatter").set(JSON_FORMATTER_NAME);
        builder.addStep(op);

        // Add the handler to the root-logger
        op = Operations.createAddOperation(LOGGER_ADDRESS);
        op.get("handlers").setEmptyList().add(JSON_HANDLER_NAME);
        builder.addStep(op);

        executeOperation(builder.build());
    }
}
