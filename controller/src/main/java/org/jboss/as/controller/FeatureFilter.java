/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

/**
 * A filter for features.
 * @author Paul Ferraro
 */
public interface FeatureFilter {
    /**
     * Determines whether the specified feature is enabled.
     * @param <F> the feature type
     * @param feature a feature
     * @return true, if the specified feature is enabled, false otherwise.
     */
    <F extends Feature> boolean enables(F feature);
}
