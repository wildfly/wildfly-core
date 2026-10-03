/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import org.jboss.as.controller.FeatureRegistry;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.version.Stability;

/**
 * A transformation description builder.
 *
 * @author Emanuel Muckenhuber
 */
public interface TransformationDescriptionBuilder extends FeatureRegistry {

    /**
     * Build the transformation description. Modifications to the builder won't affect the built description after this
     * method was called.
     *
     * @return the transformation description
     */
    TransformationDescription build();

    @Deprecated(forRemoval = true)
    class Factory {

        /**
         * Create a resource builder instance.
         *
         * @return the transformation builder
         * @deprecated Superseded by {@link ResourceTransformationDescriptionBuilderFactory#createResourceTransformationDescriptionBuilder()}
         */
        public static ResourceTransformationDescriptionBuilder createSubsystemInstance() {
            return new ResourceTransformationDescriptionBuilderImpl(Stability.DEFAULT, null);
        }

        /**
         * Create a resource builder instance.
         *
         * @param pathElement the path element of the child to be transformed
         * @return the transformation builder
         * @deprecated Superseded by {@link ResourceTransformationDescriptionBuilderFactory#createResourceTransformationDescriptionBuilder(PathElement)}
         */
        public static ResourceTransformationDescriptionBuilder createInstance(final PathElement pathElement) {
            return new ResourceTransformationDescriptionBuilderImpl(Stability.DEFAULT, pathElement);
        }

        /**
         * Create a builder instance discarding a child.
         *
         * @param pathElement the path element of the child to be transformed
         * @return the transformation builder
         * @deprecated To be removed without replacement.
         */
        public static DiscardTransformationDescriptionBuilder createDiscardInstance(PathElement pathElement) {
            return new DiscardTransformationDescriptionBuilder(Stability.DEFAULT, pathElement);
        }

        /**
         * Create a builder instance rejecting a child.
         *
         * @param pathElement the path element of the child to be transformed
         * @return the transformation builder
         * @deprecated To be removed without replacement.
         */
        public static RejectTransformationDescriptionBuilder createRejectInstance(PathElement pathElement) {
            return new RejectTransformationDescriptionBuilder(Stability.DEFAULT, pathElement);
        }

        /**
         * Create a chained builder instance for a subsystem
         *
         * @param currentVersion the current version of the subsystem.
         * @deprecated Superseded by {@link ChainedTransformationDescriptionBuilderFactory#createChainedTransformationDescriptionBuilder()}
         */
        public static ChainedTransformationDescriptionBuilder createChainedSubystemInstance(ModelVersion currentVersion) {
            return new ChainedTransformationDescriptionBuilderImpl(currentVersion, Stability.DEFAULT, null);
        }

        /**
         * Create a chained builder instance
         *
         * @param pathElement the child resource element which the chained transformers handle
         * @param currentVersion the current version of the model containing the resource being transformed.
         * @deprecated Superseded by {@link ChainedTransformationDescriptionBuilderFactory#createChainedTransformationDescriptionBuilder(PathElement)}
         */
        public static ChainedTransformationDescriptionBuilder createChainedInstance(PathElement pathElement, ModelVersion currentVersion) {
            return new ChainedTransformationDescriptionBuilderImpl(currentVersion, Stability.DEFAULT, pathElement);
        }
    }

}
