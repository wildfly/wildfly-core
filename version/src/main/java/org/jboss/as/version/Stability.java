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
 * Enumeration of stability levels.
 * @author Paul Ferraro
 */
public enum Stability {

    DEFAULT("default"),
    COMMUNITY("community"),
    PREVIEW("preview"),
    EXPERIMENTAL("experimental"),
    ;
    private final String value;

    public static Stability fromString(String value) {
        return Enum.valueOf(Stability.class, value.toUpperCase(Locale.ENGLISH));
    }

    Stability(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }

    /**
     * Indicates whether this stability enables the specified stability level.
     * @param stability a stability level
     * @return true, if this stability level enables the specified stability level, false otherwise.
     */
    public boolean enables(Stability stability) {
        // Currently assumes ascending nested sets
        return stability.ordinal() <= this.ordinal();
    }

    /**
     * Returns a complete map of a feature per stability level.
     * @param <F> the feature type
     * @param features a function returning the feature of a given stability level
     * @return a full mapping of feature per stability level
     */
    public static <F> Map<Stability, F> map(Function<Stability, F> features) {
        Map<Stability, F> map = new EnumMap<>(Stability.class);
        F lastStability = null;
        // Currently assumes ascending nested sets
        for (Stability stability : EnumSet.allOf(Stability.class)) {
            F feature = features.apply(stability);
            if (feature != null) {
                lastStability = feature;
            }
            if (lastStability != null) {
                map.put(stability, lastStability);
            }
        }
        return Collections.unmodifiableMap(map);
    }
}
