/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import org.jboss.as.version.Stability;

/**
 * Implemented by objects that register features.
 * @author Paul Ferraro
 */
public interface FeatureRegistry extends FeatureFilter {
    /**
     * Returns the feature stability supported by this feature registry.
     * @return a stability level
     */
    Stability getStability();

    /**
     * Determines whether the specified feature is enabled by the configured stability level of the feature registry.
     * @param <F> the feature type
     * @param feature a feature
     * @return true, if the specified feature is enabled, false otherwise.
     */
    @Override
    default <F extends Feature> boolean enables(F feature) {
        return this.getStability().enables(feature.getStability());
    }
}
