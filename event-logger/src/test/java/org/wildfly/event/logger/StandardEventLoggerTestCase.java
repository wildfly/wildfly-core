/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.event.logger;

import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class StandardEventLoggerTestCase extends AbstractEventLoggerTestCase {
    private static final int LOG_COUNT = 10000;

    @Test
    public void testLogger() throws Exception {
        final QueuedJsonWriter writer = new QueuedJsonWriter();
        final EventLogger logger = EventLogger.createLogger("test-logger", writer);
        testLogger(logger, writer);
    }

    @Test
    public void testMultiLogger() throws Exception {
        final QueuedJsonWriter writer = new QueuedJsonWriter();
        final EventLogger logger = EventLogger.createLogger("test-multi-logger", writer);
        testMultiLogger(logger, writer);
    }

    @Test
    public void testCollection() throws Exception {
        final QueuedJsonWriter writer = new QueuedJsonWriter();
        final EventLogger logger = EventLogger.createLogger("test-collection-logger", writer);
        final List<String> expectedValues = Arrays.asList("a", "b", "c", "1", "2", "3");
        final Map<String, Object> events = new LinkedHashMap<>();
        events.put("testCollection", expectedValues);
        logger.log(events);

        final String jsonString = writer.events.poll(TIMEOUT, TimeUnit.SECONDS);
        Assert.assertNotNull("Expected value written, but was null", jsonString);

        try (JsonReader reader = Json.createReader(new StringReader(jsonString))) {
            final JsonObject jsonObject = reader.readObject();
            final JsonArray array = jsonObject.getJsonArray("testCollection");
            Assert.assertEquals(expectedValues.size(), array.size());
            for (int i = 0; i < expectedValues.size(); i++) {
                Assert.assertEquals(expectedValues.get(i), array.getString(i));
            }
        }
    }

    @Test
    public void testMap() throws Exception {
        final QueuedJsonWriter writer = new QueuedJsonWriter();
        final EventLogger logger = EventLogger.createLogger("test-map-logger", writer);
        final Map<String, String> expectedValues = new LinkedHashMap<>();
        expectedValues.put("key1", "value1");
        expectedValues.put("key2", "value2");
        expectedValues.put("key3", "value3");
        final Map<String, Object> events = new LinkedHashMap<>();
        events.put("testMap", expectedValues);
        logger.log(events);

        final String jsonString = writer.events.poll(TIMEOUT, TimeUnit.SECONDS);
        Assert.assertNotNull("Expected value written, but was null", jsonString);

        try (JsonReader reader = Json.createReader(new StringReader(jsonString))) {
            final JsonObject jsonObject = reader.readObject();
            final JsonObject value = jsonObject.getJsonObject("testMap");
            Assert.assertEquals(expectedValues.size(), value.size());
            Assert.assertEquals(expectedValues.get("key1"), value.getString("key1"));
            Assert.assertEquals(expectedValues.get("key2"), value.getString("key2"));
            Assert.assertEquals(expectedValues.get("key3"), value.getString("key3"));
        }
    }

    @Test
    public void testArrays() throws Exception {
        final QueuedJsonWriter writer = new QueuedJsonWriter();
        final EventLogger logger = EventLogger.createLogger("test-array-logger", writer);
        final Integer[] expectedIntValues = new Integer[] {1, 2, 3, 4, 5, 6};
        final Map<String, Object> events = new LinkedHashMap<>();
        events.put("testIntArray", expectedIntValues);
        final String[] expectedStringValues = new String[] {"a", "b", "c"};
        events.put("testStringArray", expectedStringValues);
        logger.log(events);

        final String jsonString = writer.events.poll(TIMEOUT, TimeUnit.SECONDS);
        Assert.assertNotNull("Expected value written, but was null", jsonString);

        try (JsonReader reader = Json.createReader(new StringReader(jsonString))) {
            final JsonObject jsonObject = reader.readObject();

            // Test int values
            JsonArray array = jsonObject.getJsonArray("testIntArray");
            Assert.assertEquals(expectedIntValues.length, array.size());
            for (int i = 0; i < expectedIntValues.length; i++) {
                Assert.assertEquals((int) expectedIntValues[i], array.getInt(i));
            }

            // Test string values
            array = jsonObject.getJsonArray("testStringArray");
            Assert.assertEquals(expectedStringValues.length, array.size());
            for (int i = 0; i < expectedStringValues.length; i++) {
                Assert.assertEquals(expectedStringValues[i], array.getString(i));
            }
        }
    }

    private static void testMultiLogger(final EventLogger logger, final QueuedJsonWriter writer) throws Exception {
        final ExecutorService executor = createExecutor();
        try {
            final Map<Integer, TestValues> createValues = new HashMap<>();
            for (int i = 0; i < LOG_COUNT; i++) {
                final TestValues values = new TestValues()
                        .add("eventSource", logger.getEventSource(), JsonObject::getString)
                        .add("count", i, JsonObject::getInt);
                createValues.put(i, values);
                // Use a supplier for every 5th event
                final boolean useSupplier = (i % 5 == 0);
                executor.submit(() -> {
                    if (useSupplier) {
                        logger.log(values::asMap);
                    } else {
                        logger.log(values.asMap());
                    }
                });
            }

            for (int i = 0; i < LOG_COUNT; i++) {
                final String jsonString = writer.events.poll(TIMEOUT, TimeUnit.SECONDS);
                Assert.assertNotNull("Expected value written, but was null", jsonString);

                try (JsonReader reader = Json.createReader(new StringReader(jsonString))) {
                    final JsonObject jsonObject = reader.readObject();
                    final int count = jsonObject.getInt("count");
                    final TestValues values = createValues.remove(count);
                    Assert.assertNotNull("Failed to find value for entry " + count, values);
                    for (TestValue<?> testValue : values) {
                        testValue.compare(jsonObject);
                    }
                }
            }
            Assert.assertTrue("Values were created that were not logged: " + createValues, createValues.isEmpty());
        } finally {
            executor.shutdown();
            Assert.assertTrue(String.format("Executed did not complete within %d seconds", TIMEOUT),
                    executor.awaitTermination(TIMEOUT, TimeUnit.SECONDS));
        }
    }
}
