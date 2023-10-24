/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import org.jboss.as.version.Quality;

/**
 * Implemented by objects that register features.
 * @author Paul Ferraro
 */
public interface FeatureRegistry {
    /**
     * Returns the feature quality supported by this feature registry.
     * @return a quality level
     */
    default Quality getQuality() {
        // TODO Default implementation is only here to prevent wildfly-full integration test failures
        // Remove before branch is merged
        return Quality.DEFAULT;
    }

    /**
     * Determines whether the specified feature is enabled by the configured quality level of the feature registry.
     * @param <F> the feature type
     * @param feature a feature
     * @return true, if the specified feature is enabled, false otherwise.
     */
    default <F extends Feature> boolean enables(F feature) {
        return this.getQuality().enables(feature.getQuality());
    }
}
