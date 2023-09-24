/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.core.model.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;

import org.jboss.as.controller.ModelVersion;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Makes adjustments to a model node to bring it in conformance with another model node, in order to prevent
 * spurious differences between the two being detected when a subsequent comparison is performed.
 *
 * @author Brian Stansberry (c) 2012 Red Hat Inc.
 */
@FunctionalInterface
public interface ModelHarmonizer {

    /**
     * Make some adjustment to {@code target} to bring it in conformance with {@code source} so
     * subsequent comparisons between {@code source} and {@code target} will not report spurious differences.
     *
     * @param modelVersion the version of the models
     * @param source the source mode
     * @param target the target model
     */
    void harmonizeModel(ModelVersion modelVersion, ModelNode source, ModelNode target);

    ModelHarmonizer UNDEFINED_TO_EMPTY = new ModelHarmonizer() {
        @Override
        public void harmonizeModel(ModelVersion modelVersion, ModelNode source, ModelNode target) {
            if (source.getType() == ModelType.OBJECT && source.asInt() == 0 && !target.isDefined()) {
                target.setEmptyObject();
            }
        }
    };

    ModelHarmonizer MISSING_NAME = new ModelHarmonizer() {
        @Override
        public void harmonizeModel(ModelVersion modelVersion, ModelNode source, ModelNode target) {
            if (source.hasDefined(NAME) && !target.hasDefined(NAME)) {
                target.get(NAME).set(source.get(NAME));
            }
        }
    };
}
