/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package org.wildfly.event.logger;

import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.junit.Assert;
import org.wildfly.common.cpu.ProcessorInfo;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractEventLoggerTestCase {
    static final long TIMEOUT = 5L;

    static void testLogger(final EventLogger logger, final QueuedJsonWriter writer) throws Exception {
        final ZonedDateTime now = ZonedDateTime.now();

        final TestValues values = new TestValues()
                .add("eventSource", logger.getEventSource(), JsonObject::getString)
                .add("testBoolean", true, JsonObject::getBoolean)
                .add("testString", "Test string", JsonObject::getString)
                .add("testInt", 33, JsonObject::getInt)
                .add("testLong", 138L, (json, key) -> json.getJsonNumber(key).longValue())
                .add("testDouble", 6.50d, Double::doubleToLongBits, (json, key) -> Double.doubleToLongBits(json.getJsonNumber(key).doubleValue()))
                .add("testDate", now, ZonedDateTime::toString, JsonObject::getString)
                .add("testDecimal", new BigDecimal("33.50"), (json, key) -> json.getJsonNumber(key).bigDecimalValue())
                .add("testBigInt", new BigInteger("8675309"), (json, key) -> json.getJsonNumber(key).bigIntegerValue());

        logger.log(values.asMap());

        final String jsonString = writer.events.poll(TIMEOUT, TimeUnit.SECONDS);
        Assert.assertNotNull("Expected value written, but was null", jsonString);

        try (JsonReader reader = Json.createReader(new StringReader(jsonString))) {
            final JsonObject jsonObject = reader.readObject();
            for (TestValue<?> testValue : values) {
                testValue.compare(jsonObject);
            }
        }

        Assert.assertTrue("Expected no more events: " + writer.events, writer.events.isEmpty());
    }

    static ExecutorService createExecutor() {
        return Executors.newFixedThreadPool(Math.max(2, ProcessorInfo.availableProcessors() - 2));
    }

    static class TestValue<V> {
        final String key;
        final V value;
        final BiFunction<JsonObject, String, Object> mapper;
        final Function<V, Object> valueConverter;

        private TestValue(final String key, final V value, final BiFunction<JsonObject, String, Object> mapper) {
            this(key, value, (v) -> v, mapper);
        }

        private TestValue(final String key, final V value, final Function<V, Object> valueConverter, final BiFunction<JsonObject, String, Object> mapper) {
            this.key = key;
            this.value = value;
            this.valueConverter = valueConverter;
            this.mapper = mapper;
        }

        void compare(final JsonObject json) {
            Assert.assertEquals(valueConverter.apply(value), mapper.apply(json, key));
        }

        @Override
        public String toString() {
            return "TestValue[key=" + key + ", value=" + value + "]";
        }
    }

    static class TestValues implements Iterable<TestValue<?>> {
        private final Collection<TestValue<?>> values;

        TestValues() {
            values = new ArrayList<>();
        }

        <V> TestValues add(final String key, final V value, final BiFunction<JsonObject, String, Object> mapper) {
            values.add(new TestValue<>(key, value, mapper));
            return this;
        }

        <V> TestValues add(final String key, final V value, final Function<V, Object> valueConverter, final BiFunction<JsonObject, String, Object> mapper) {
            values.add(new TestValue<>(key, value, valueConverter, mapper));
            return this;
        }

        Map<String, Object> asMap() {
            final Map<String, Object> result = new LinkedHashMap<>();
            for (TestValue<?> testValue : values) {
                result.put(testValue.key, testValue.value);
            }
            return result;
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public Iterator<TestValue<?>> iterator() {
            return values.iterator();
        }

        @Override
        public String toString() {
            return "TestValues[values=" + values + "]";
        }
    }
}
