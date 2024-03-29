/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.xml;

import org.jboss.as.controller.Feature;
import org.jboss.staxmapper.Versioned;

/**
 * A versioned feature.
 * @param <V> the version type
 * @param <F> the versioned feature type
 */
public interface VersionedFeature<V extends Comparable<V>, F extends VersionedFeature<V, F>> extends Versioned<V, F>, Feature {

    @Override
    default boolean since(F feature) {
        // Also ensure that our stability enables the stability of the feature
        return Versioned.super.since(feature) && this.getStability().enables(feature.getStability());
    }
}
