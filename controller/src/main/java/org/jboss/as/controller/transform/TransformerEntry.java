/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform;

/**
 * @author Emanuel Muckenhuber
 */
public interface TransformerEntry {

    PathAddressTransformer getPathTransformation();
    ResourceTransformer getResourceTransformer();

    TransformerEntry ALL_DEFAULTS = new TransformerEntry() {
        @Override
        public PathAddressTransformer getPathTransformation() {
            return PathAddressTransformer.DEFAULT;
        }

        @Override
        public ResourceTransformer getResourceTransformer() {
            return ResourceTransformer.DEFAULT;
        }
    };

    TransformerEntry DISCARD = new TransformerEntry() {

        @Override
        public ResourceTransformer getResourceTransformer() {
            return ResourceTransformer.DISCARD;
        }

        @Override
        public PathAddressTransformer getPathTransformation() {
            return PathAddressTransformer.DEFAULT;
        }
    };
}
