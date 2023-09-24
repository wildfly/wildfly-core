/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.ModelVersionRange;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.PathAddressTransformer;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;
import org.jboss.as.controller.transform.TransformersSubRegistration;

/**
 * The final transformation description including child resources.
 *
 * @author Emanuel Muckenhuber
 */
public interface TransformationDescription {

    /**
     * Get the path for this transformation description.
     *
     * @return the path element
     */
    PathElement getPath();

    /**
     * Get the path transformation for this level.
     *
     * @return the path transformation
     */
    PathAddressTransformer getPathAddressTransformer();

    /**
     * Get the default operation transformer.
     *
     * @return the operation transformer
     */
    OperationTransformer getOperationTransformer();

    /**
     * Get the resource transformer.
     *
     * @return the resource transformer
     */
    ResourceTransformer getResourceTransformer();

    /**
     * Get the operation transformers for specific operations.
     *
     * @return the operation transformer overrides
     */
    Map<String, OperationTransformer> getOperationTransformers();

    /**
     * Get the children descriptions.
     *
     * @return the children
     */
    List<TransformationDescription> getChildren();

    /**
     * If this is a discarded or rejected resource it returns {@code true}
     *
     * @return {@code true} if this is a discarded or rejected resource
     */
    boolean isInherited();

    /**
     * operations that must be flat out discarded and not forwarded
     *
     * @return set of discarded operations
     */
    Set<String> getDiscardedOperations();

    /**
     * Return {@code} true if this description is a placeholder. This is currently only true for chained descriptions
     *
     * @return {@code true} if a placeholder.
     */
    boolean isPlaceHolder();

    final class Tools {

        private Tools() {
            //
        }

        /**
         * Register a transformation description as a sub-resource at a given {@linkplain TransformersSubRegistration}.
         *
         * @param description the transformation description.
         * @param parent the parent registration
         * @return the created sub registration
         */
        public static TransformersSubRegistration register(final TransformationDescription description, TransformersSubRegistration parent) {
            final TransformersSubRegistration registration;
            if (description.getPath() == null) { //root registration
                registration = parent;
            } else {
                registration = parent.registerSubResource(
                        description.getPath(),
                        description.getPathAddressTransformer(),
                        description.getResourceTransformer(),
                        description.getOperationTransformer(),
                        description.isInherited(),
                        description.isPlaceHolder());
            }
            for (final Map.Entry<String, OperationTransformer> entry : description.getOperationTransformers().entrySet()) {
                registration.registerOperationTransformer(entry.getKey(), entry.getValue());
            }
            registration.discardOperations(description.getDiscardedOperations().toArray(new String[description.getDiscardedOperations().size()]));
            for (final TransformationDescription child : description.getChildren()) {
                register(child, registration);
            }
            return registration;
        }

        /**
         * Register a transformation description as a sub-resource at a given {@linkplain SubsystemRegistration}.
         *
         * @param description the subsystem transformation description
         * @param registration the subsystem registrations
         * @param versions the model versions the transformation description applies to
         * @return the created sub registration
         */
        public static TransformersSubRegistration register(TransformationDescription description, SubsystemTransformerRegistration registration, ModelVersion... versions) {
            return register(description, registration, ModelVersionRange.Versions.range(versions));
        }

        private static TransformersSubRegistration getTransformersSubRegistration(TransformationDescription description, TransformersSubRegistration subRegistration) {
            for (final Map.Entry<String, OperationTransformer> entry : description.getOperationTransformers().entrySet()) {
                subRegistration.registerOperationTransformer(entry.getKey(), entry.getValue());
            }
            for (final TransformationDescription child : description.getChildren()) {
                register(child, subRegistration);
            }
            subRegistration.discardOperations(description.getDiscardedOperations().toArray(new String[description.getDiscardedOperations().size()]));
            return subRegistration;
        }

        /**
         * Register a transformation description as a sub-resource at a given {@linkplain SubsystemRegistration}.
         *
         * @param description the subsystem transformation description
         * @param registration the subsystem registrations
         * @param range the model version range the transformation applies to
         * @return the create sub registration
         */
        public static TransformersSubRegistration register(TransformationDescription description, SubsystemTransformerRegistration registration, ModelVersionRange range) {
            final TransformersSubRegistration subRegistration = registration.registerModelTransformers(range, description.getResourceTransformer(),
                    description.getOperationTransformer(), description.isPlaceHolder());

            return getTransformersSubRegistration(description, subRegistration);
        }
    }
}
