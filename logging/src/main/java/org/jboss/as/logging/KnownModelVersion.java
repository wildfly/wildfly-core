/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging;

import org.jboss.as.controller.ModelVersion;

/**
 * Known model versions for the logging extension.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public enum KnownModelVersion {
    VERSION_1_5_0(ModelVersion.create(1, 5, 0), true), // EAP 6.4
    VERSION_2_0_0(ModelVersion.create(2, 0, 0), true),
    VERSION_3_0_0(ModelVersion.create(3, 0, 0), false),
    VERSION_4_0_0(ModelVersion.create(4, 0, 0), false),
    VERSION_5_0_0(ModelVersion.create(5, 0, 0), true),
    VERSION_6_0_0(ModelVersion.create(6, 0, 0), true),
    VERSION_7_0_0(ModelVersion.create(7, 0, 0), true),
    VERSION_8_0_0(ModelVersion.create(8, 0, 0), false),
    VERSION_9_0_0(ModelVersion.create(9, 0, 0), false),
    ;
    private final ModelVersion modelVersion;
    private final boolean hasTransformers;

    KnownModelVersion(final ModelVersion modelVersion, final boolean hasTransformers) {
        this.modelVersion = modelVersion;
        this.hasTransformers = hasTransformers;
    }

    /**
     * Returns {@code true} if transformers should be registered against the model version.
     *
     * @return {@code true} if transformers should be registered, otherwise {@code false}
     */
    public boolean hasTransformers() {
        return hasTransformers;
    }

    /**
     * The model version.
     *
     * @return the model version
     */
    public ModelVersion getModelVersion() {
        return modelVersion;
    }
}
