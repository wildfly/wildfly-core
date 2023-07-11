/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import org.jboss.as.version.FeatureStream;

/**
 * Implemented by objects that register features.
 * @author Paul Ferraro
 */
public interface FeatureRegistry {
    /**
     * Returns the feature stream associated with this server.
     * @return a feature stream
     */
    default FeatureStream getFeatureStream() {
        return FeatureStream.DEFAULT;
    }

    /**
     * Determines whether the specified feature is enabled by the feature stream associated with this container
     * @param <F> the feature type
     * @param feature a feature
     * @return true, if the specified feature is enabled, false otherwise.
     */
    default <F extends Feature> boolean enables(F feature) {
        return this.getFeatureStream().enables(feature.getFeatureStream());
    }
}
