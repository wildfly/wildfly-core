/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;

/**
 * A transformation description builder.
 *
 * @author Emanuel Muckenhuber
 */
public interface TransformationDescriptionBuilder {

    /**
     * Build the transformation description. Modifications to the builder won't affect the built description after this
     * method was called.
     *
     * @return the transformation description
     */
    TransformationDescription build();

    class Factory {

        /**
         * Create a resource builder instance.
         *
         * @return the transformation builder
         */
        public static ResourceTransformationDescriptionBuilder createSubsystemInstance() {
            return new ResourceTransformationDescriptionBuilderImpl(null);
        }

        /**
         * Create a resource builder instance.
         *
         * @param pathElement the path element of the child to be transformed
         * @return the transformation builder
         */
        public static ResourceTransformationDescriptionBuilder createInstance(final PathElement pathElement) {
            return new ResourceTransformationDescriptionBuilderImpl(pathElement);
        }

        /**
         * Create a builder instance discarding a child.
         *
         * @param pathElement the path element of the child to be transformed
         * @return the transformation builder
         */
        public static DiscardTransformationDescriptionBuilder createDiscardInstance(PathElement pathElement) {
            return new DiscardTransformationDescriptionBuilder(pathElement);
        }

        /**
         * Create a builder instance rejecting a child.
         *
         * @param pathElement the path element of the child to be transformed
         * @return the transformation builder
         */
        public static RejectTransformationDescriptionBuilder createRejectInstance(PathElement pathElement) {
            return new RejectTransformationDescriptionBuilder(pathElement);
        }

        /**
         * Create a chained builder instance for a subsystem
         *
         * @param currentVersion the current version of the subsystem.
         */
        public static ChainedTransformationDescriptionBuilder createChainedSubystemInstance(ModelVersion currentVersion) {
            return new ChainedTransformationDescriptionBuilderImpl(currentVersion, null);
        }

        /**
         * Create a chained builder instance
         *
         * @param pathElement the child resource element which the chained transformers handle
         * @param currentVersion the current version of the model containing the resource being transformed.
         */
        public static ChainedTransformationDescriptionBuilder createChainedInstance(PathElement pathElement, ModelVersion currentVersion) {
            return new ChainedTransformationDescriptionBuilderImpl(currentVersion, pathElement);
        }
    }

}
