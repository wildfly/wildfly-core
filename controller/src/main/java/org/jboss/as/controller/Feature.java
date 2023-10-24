/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import java.util.EnumMap;
import java.util.Map;

import org.jboss.as.version.Quality;

/**
 * @author Paul Ferraro
 */
public interface Feature {
    /**
     * Returns the quality of this feature.
     * @return a quality level
     */
    default Quality getQuality() {
        return Quality.DEFAULT;
    }

    /**
     * Returns a complete map of a feature per quality.
     * @param <F> the feature type
     * @param features a collection of features of different qualities
     * @return a full mapping of feature per quality
     */
    static <F extends Feature> Map<Quality, F> map(Iterable<F> features) {
        Map<Quality, F> map = new EnumMap<>(Quality.class);
        for (F feature : features) {
            map.put(feature.getQuality(), feature);
        }
        return Quality.map(map::get);
    }
}
