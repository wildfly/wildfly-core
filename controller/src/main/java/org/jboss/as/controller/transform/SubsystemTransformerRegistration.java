/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.ModelVersionRange;

/**
 * Subsystem transformers registration API
 *
 * Methods on this interface provide main transformers registration points for all {@link ExtensionTransformerRegistration} implementations.
 *
 * @author Tomaz Cerar (c) 2016 Red Hat Inc.
 */
public interface SubsystemTransformerRegistration {

    /**
     * Register transformers for a specific model versions.
     *
     * @param version             the model version range
     * @param resourceTransformer the subsystem resource transformer
     * @return the transformers registry
     */
    TransformersSubRegistration registerModelTransformers(ModelVersionRange version, ResourceTransformer resourceTransformer);

    /**
     * Register transformers for a given model version.
     *
     * @param version              the model version
     * @param resourceTransformer  the subsystem resource transformer
     * @param operationTransformer the subsystem operation transformer
     * @param placeholder          whether or not the transformers are placeholders
     * @return the transformers registry
     */
    TransformersSubRegistration registerModelTransformers(ModelVersionRange version, ResourceTransformer resourceTransformer, OperationTransformer operationTransformer, boolean placeholder);

    /**
     * Register transformers for a given model version.
     *
     * @param version             the model version
     * @param combinedTransformer the combined transformer
     * @return the subsystem registration
     */
    TransformersSubRegistration registerModelTransformers(ModelVersionRange version, CombinedTransformer combinedTransformer);

    /**
     * Get the version of the subsystem
     *
     * @return the version
     */
    ModelVersion getCurrentSubsystemVersion();

}
