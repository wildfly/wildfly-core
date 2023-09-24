/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.transform.description;

import java.util.Map;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.transform.SubsystemTransformerRegistration;

/**
 * A builder used to create chained transformers. Created using
 * {@link TransformationDescriptionBuilder.Factory#createChainedInstance(org.jboss.as.controller.PathElement, ModelVersion)} or
 * {@link TransformationDescriptionBuilder.Factory#createChainedSubystemInstance(ModelVersion)}. The {@code ModelVersion} parameter
 * to these operations is the 'current' model version.
 * Internally this uses a {@link org.jboss.as.controller.registry.OperationTransformerRegistry.PlaceholderResolver} so
 * if this is used, all children must also use chained transformers. Typically you should create a chain for the subsystem
 * and add child transformations using the chained builder.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface ChainedTransformationDescriptionBuilder {

    /**
     * Add a transformer for a version delta
     *
     * @param fromVersion the version to transform from
     * @param toVersion the version to transform to
     * @return the builder for the transformation
     */
    ResourceTransformationDescriptionBuilder createBuilder(ModelVersion fromVersion, ModelVersion toVersion);


    /**
     * Build the transformer chains for chain of versions.
     *
     * @param versions the versions in the transformer chain. This should not include the 'current' version mentioned in the class javadoc.
     * @return a map of transformation descriptions for each model version
     *
     */
    Map<ModelVersion, TransformationDescription> build(ModelVersion...versions);


    /**
     * Builds and registers the transformer chains for this builder against a subsystem
     *
     *@param registration the subsystem registrations
     *@param chains the version chains (not including the 'current' version as in {@link #build(ModelVersion...)})
     */
    void buildAndRegister(SubsystemTransformerRegistration registration, ModelVersion[]...chains);
}
