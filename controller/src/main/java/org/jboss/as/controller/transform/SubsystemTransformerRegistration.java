/*
 * JBoss, Home of Professional Open Source
 * Copyright 2016, Red Hat, Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
