/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import org.jboss.as.controller.FeatureRegistry;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;

/**
 * A factory for creating a chained transformation description builder.
 */
public interface ChainedTransformationDescriptionBuilderFactory extends FeatureRegistry {

    /**
     * Creates a root builder of chained transformation descriptions.
     * @return a builder of chained transformation descriptions.
     */
    default ChainedTransformationDescriptionBuilder createChainedTransformationDescriptionBuilder() {
        return this.createChainedTransformationDescriptionBuilder(null);
    }

    /**
     * Creates a builder of chained transformation descriptions for the specified resource
     * @param path the path of the target resource
     * @return a builder of chained transformation descriptions.
     */
    default ChainedTransformationDescriptionBuilder createChainedTransformationDescriptionBuilder(PathElement path) {
        return new ChainedTransformationDescriptionBuilderImpl(this.getCurrentVersion(), this.getStability(), path);
    }

    /**
     * The current version of the associated model.
     * @return a model version
     */
    ModelVersion getCurrentVersion();
}
