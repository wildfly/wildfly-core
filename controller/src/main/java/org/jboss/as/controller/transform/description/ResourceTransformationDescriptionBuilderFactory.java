/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import org.jboss.as.controller.FeatureRegistry;
import org.jboss.as.controller.PathElement;

/**
 * A factory for creating transformation description builders.
 */
public interface ResourceTransformationDescriptionBuilderFactory extends FeatureRegistry {

    /**
     * Creates a builder of a transformation description for a root resource.
     * @return a builder of a transformation description.
     */
    default ResourceTransformationDescriptionBuilder createResourceTransformationDescriptionBuilder() {
        return this.createResourceTransformationDescriptionBuilder(null);
    }

    /**
     * Creates a builder of a transformation description for a resource.
     * @param path the path of the target resource
     * @return a builder of a transformation description.
     */
    default ResourceTransformationDescriptionBuilder createResourceTransformationDescriptionBuilder(PathElement path) {
        return new ResourceTransformationDescriptionBuilderImpl(this.getStability(), path);
    }
}
