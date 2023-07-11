/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.version;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Paul Ferraro
 */
public enum FeatureStream {

    STABLE("stable"),
    PREVIEW("preview"),
    EXPERIMENTAL("experimental"),
    ;
    public static final FeatureStream DEFAULT = STABLE;

    private final String value;

    FeatureStream(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }

    /**
     * Indicates whether this feature stream enables the specified feature stream.
     * @param stream a feature stream
     * @return true, if this feature stream enables the specified feature stream, false otherwise.
     */
    public boolean enables(FeatureStream stream) {
        // Currently assumes ascending nested sets
        return stream.ordinal() <= this.ordinal();
    }

    /**
     * Returns a complete map of features per stream.
     * @param <F> the feature type
     * @param factory function returning the feature for a given feature stream
     * @return a mapping of features per stream
     */
    public static <F> Map<FeatureStream, F> map(Function<FeatureStream, F> factory) {
        Map<FeatureStream, F> map = new EnumMap<>(FeatureStream.class);
        F lastFeature = null;
        // Currently assumes ascending nested sets
        for (FeatureStream stream : EnumSet.allOf(FeatureStream.class)) {
            F feature = factory.apply(stream);
            if (feature != null) {
                lastFeature = feature;
            }
            if (lastFeature != null) {
                map.put(stream, lastFeature);
            }
        }
        return Collections.unmodifiableMap(map);
    }
}
