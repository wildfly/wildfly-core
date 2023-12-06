/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import java.util.EnumMap;
import java.util.Map;

import org.jboss.as.version.Stability;

/**
 * @author Paul Ferraro
 */
public interface Feature {
    /**
     * Returns the stability level of this feature.
     * @return a stability level
     */
    default Stability getStability() {
        return Stability.DEFAULT;
    }

    /**
     * Returns a complete map of a feature per stability level.
     * @param <F> the feature type
     * @param features a collection of features of different stability levels.
     * @return a full mapping of feature per stability level.
     */
    static <F extends Feature> Map<Stability, F> map(Iterable<F> features) {
        Map<Stability, F> map = new EnumMap<>(Stability.class);
        for (F feature : features) {
            map.put(feature.getStability(), feature);
        }
        return Stability.map(map::get);
    }
}
