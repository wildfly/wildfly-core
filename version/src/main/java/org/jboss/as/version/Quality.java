/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.version;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Enumeration of quality levels.
 * @author Paul Ferraro
 */
public enum Quality {

    DEFAULT("default"),
    COMMUNITY("community"),
    PREVIEW("preview"),
    EXPERIMENTAL("experimental"),
    ;
    private final String value;

    public static Quality fromString(String value) {
        return Enum.valueOf(Quality.class, value.toUpperCase(Locale.ENGLISH));
    }

    Quality(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }

    /**
     * Indicates whether this quality enables the specified quality.
     * @param quality a quality level
     * @return true, if this quality enables the specified quality, false otherwise.
     */
    public boolean enables(Quality quality) {
        // Currently assumes ascending nested sets
        return quality.ordinal() <= this.ordinal();
    }

    /**
     * Returns a complete map of a feature per quality.
     * @param <F> the feature type
     * @param features a function returning the feature of a given quality
     * @return a full mapping of feature per quality
     */
    public static <F> Map<Quality, F> map(Function<Quality, F> features) {
        Map<Quality, F> map = new EnumMap<>(Quality.class);
        F lastQuality = null;
        // Currently assumes ascending nested sets
        for (Quality quality : EnumSet.allOf(Quality.class)) {
            F feature = features.apply(quality);
            if (feature != null) {
                lastQuality = feature;
            }
            if (lastQuality != null) {
                map.put(quality, lastQuality);
            }
        }
        return Collections.unmodifiableMap(map);
    }
}
