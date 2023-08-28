/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import java.util.EnumMap;
import java.util.Map;

import org.jboss.as.version.FeatureStream;

/**
 * @author Paul Ferraro
 */
public interface Feature {
    /**
     * Returns the stream for which this feature is enabled.
     * @return a feature stream
     */
    default FeatureStream getFeatureStream() {
        return FeatureStream.DEFAULT;
    }

    /**
     * Returns a map of features to enable per stream.
     * @param <F> the feature type
     * @param features a collection of stream-specific features
     * @return a mapping of features per stream
     */
    static <F extends Feature> Map<FeatureStream, F> map(Iterable<F> features) {
        Map<FeatureStream, F> map = new EnumMap<>(FeatureStream.class);
        for (F feature : features) {
            map.put(feature.getFeatureStream(), feature);
        }
        return FeatureStream.map(map::get);
    }
}
