/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.event.logger;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

/**
 * A formatter which transforms the event into a JSON string.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class JsonEventFormatter implements EventFormatter {

    private final JsonBuilderFactory factory;
    private final Map<String, Object> metaData;
    private final String timestampKey;
    private final DateTimeFormatter formatter;
    private final boolean includeTimestamp;

    private JsonEventFormatter(final Map<String, Object> metaData, final String timestampKey,
                               final DateTimeFormatter formatter, final boolean includeTimestamp) {
        this.metaData = metaData;
        this.timestampKey = timestampKey;
        this.formatter = formatter;
        this.includeTimestamp = includeTimestamp;
        factory = Json.createBuilderFactory(Collections.emptyMap());
    }

    /**
     * Creates a new builder to build a {@link JsonEventFormatter}.
     *
     * @return a new builder
     */
    @SuppressWarnings("WeakerAccess")
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String format(final Event event) {
        final JsonObjectBuilder builder = factory.createObjectBuilder();
        builder.add("eventSource", event.getSource());
        if (includeTimestamp) {
            builder.add(timestampKey, formatter.format(event.getInstant()));
        }
        add(builder, metaData);
        add(builder, event.getData());
        return builder.build().toString();
    }

    private void add(final JsonObjectBuilder builder, final Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();
            if (value == null) {
                builder.addNull(key);
            } else if (value instanceof Boolean) {
                builder.add(key, (Boolean) value);
            } else if (value instanceof Double) {
                builder.add(key, (Double) value);
            } else if (value instanceof Integer) {
                builder.add(key, (Integer) value);
            } else if (value instanceof Long) {
                builder.add(key, (Long) value);
            } else if (value instanceof String) {
                builder.add(key, (String) value);
            } else if (value instanceof BigDecimal) {
                builder.add(key, (BigDecimal) value);
            } else if (value instanceof BigInteger) {
                builder.add(key, (BigInteger) value);
            } else if (value instanceof Collection) {
                builder.add(key, factory.createArrayBuilder((Collection<?>) value));
            } else if (value instanceof Map) {
                final Map<?, ?> mapValue = (Map<?, ?>) value;
                final JsonObjectBuilder valueBuilder = factory.createObjectBuilder();
                // Convert the map to a string/object map
                final Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<?, ?> valueEntry : mapValue.entrySet()) {
                    final Object valueKey = valueEntry.getKey();
                    final Object valueValue = valueEntry.getValue();
                    if (valueKey instanceof String) {
                        map.put((String) valueKey, valueValue);
                    } else {
                        map.put(String.valueOf(valueKey), valueValue);
                    }
                }
                add(valueBuilder, map);
                builder.add(key, valueBuilder);
            } else if (value instanceof JsonArrayBuilder) {
                builder.add(key, (JsonArrayBuilder) value);
            } else if (value instanceof JsonObjectBuilder) {
                builder.add(key, (JsonObjectBuilder) value);
            } else if (value instanceof JsonValue) {
                builder.add(key, (JsonValue) value);
            } else if (value.getClass().isArray()) {
                // We'll rely on the array builder to convert to the correct object type
                builder.add(key, factory.createArrayBuilder(Arrays.asList((Object[]) value)));
            } else {
                builder.add(key, String.valueOf(value));
            }
        }
    }

    /**
     * Builder used to create the {@link JsonEventFormatter}.
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class Builder {
        private Map<String, Object> metaData;
        private String timestampKey;
        private DateTimeFormatter formatter;
        private ZoneId zoneId;
        private boolean includeTimestamp = true;

        private Builder() {
            metaData = new LinkedHashMap<>();
        }

        /**
         * Adds meta-data to the final output.
         *
         * @param key   the key to add
         * @param value the value for the key
         *
         * @return this builder
         */
        public Builder addMetaData(final String key, final Object value) {
            if (metaData == null) {
                metaData = new LinkedHashMap<>();
            }
            metaData.put(key, value);
            return this;
        }

        /**
         * Adds meta-data to the final output.
         *
         * @param metaData the meta-data to add
         *
         * @return this builder
         */
        public Builder addMetaData(final Map<String, Object> metaData) {
            if (this.metaData == null) {
                this.metaData = new LinkedHashMap<>();
            }
            this.metaData.putAll(metaData);
            return this;
        }

        /**
         * Sets the key for the timestamp for the event. The default is {@code timestamp}.
         *
         * @param timestampKey the key name or {@code null} to revert to the default
         *
         * @return this builder
         */
        public Builder setTimestampKey(final String timestampKey) {
            this.timestampKey = timestampKey;
            return this;
        }

        /**
         * Set the formatter used to format the timestamp on the event. The default is
         * {@linkplain DateTimeFormatter#ISO_OFFSET_DATE_TIME ISO-8601}.
         * <p>
         * Note the {@linkplain #setZoneId(ZoneId) zone id} is {@linkplain DateTimeFormatter#withZone(ZoneId) zone id}
         * on the formatter.
         * </p>
         *
         * @param formatter the formatter to use or {@code null} to revert to the default.
         *
         * @return this builder
         */
        public Builder setTimestampFormatter(final DateTimeFormatter formatter) {
            this.formatter = formatter;
            return this;
        }

        /**
         * Set the zone id for the timestamp. The default is {@link ZoneId#systemDefault()}.
         *
         * @param zoneId the zone id to use or {@code null} to revert to the default
         *
         * @return this builder
         */
        public Builder setZoneId(final ZoneId zoneId) {
            this.zoneId = zoneId;
            return this;
        }

        /**
         * Sets whether or not the timestamp should be added to the output. The default is {@code true}. If set to
         * {@code false} the {@linkplain #setZoneId(ZoneId) zone id} and
         * {@linkplain #setTimestampFormatter(DateTimeFormatter) format} are ignored.
         *
         * @param includeTimestamp {@code true} to include the timestamp or {@code false} to leave the timestamp off
         *
         * @return this builder
         */
        public Builder setIncludeTimestamp(final boolean includeTimestamp) {
            this.includeTimestamp = includeTimestamp;
            return this;
        }

        /**
         * Creates the {@link JsonEventFormatter}.
         *
         * @return the newly created formatter
         */
        public JsonEventFormatter build() {
            final Map<String, Object> metaData = (this.metaData == null ? Collections.emptyMap() : new LinkedHashMap<>(this.metaData));
            final String timestampKey = (this.timestampKey == null ? "timestamp" : this.timestampKey);
            final DateTimeFormatter formatter = (this.formatter == null ? DateTimeFormatter.ISO_OFFSET_DATE_TIME : this.formatter);
            final ZoneId zoneId = (this.zoneId == null ? ZoneId.systemDefault() : this.zoneId);
            return new JsonEventFormatter(metaData, timestampKey, formatter.withZone(zoneId), includeTimestamp);
        }
    }
}
