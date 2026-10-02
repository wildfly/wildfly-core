/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.event.logger;

import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonObject;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link LoggerEventWriter}.
 */
public class LoggerEventWriterTestCase {

    @Test
    public void writesFormattedLineToNamedLogger() {
        final String category = "org.wildfly.event.logger.test." + System.nanoTime();
        final EventFormatter formatter = JsonEventFormatter.builder().build();
        final LoggerEventWriter writer = LoggerEventWriter.of(category, formatter);

        final Logger jul = Logger.getLogger(category);
        final CapturingHandler handler = new CapturingHandler();
        jul.addHandler(handler);
        jul.setUseParentHandlers(false);
        jul.setLevel(Level.ALL);

        try {
            final Map<String, Object> data = new LinkedHashMap<>();
            data.put("key", "value");
            data.put("count", 42);
            writer.write(makeEvent(data));
        } finally {
            jul.removeHandler(handler);
        }

        Assert.assertEquals("expected exactly one log record", 1, handler.records.size());
        final LogRecord record = handler.records.get(0);
        Assert.assertEquals(Level.INFO, record.getLevel());

        final JsonObject obj = Json.createReader(new StringReader(record.getMessage())).readObject();
        Assert.assertEquals("value", obj.getString("key"));
        Assert.assertEquals(42, obj.getInt("count"));
    }

    @Test
    public void closeDoesNotThrow() throws Exception {
        final LoggerEventWriter writer = LoggerEventWriter.of(
                "org.wildfly.event.logger.test.close", JsonEventFormatter.builder().build());
        writer.close();
    }

    // -------------------------------------------------------------------------

    private static Event makeEvent(final Map<String, Object> data) {
        return new Event() {
            @Override
            public String getSource() {
                return "test";
            }

            @Override
            public Instant getInstant() {
                return Instant.EPOCH;
            }

            @Override
            public Map<String, Object> getData() {
                return data;
            }
        };
    }

    private static final class CapturingHandler extends Handler {
        final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(final LogRecord r) {
            records.add(r);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
