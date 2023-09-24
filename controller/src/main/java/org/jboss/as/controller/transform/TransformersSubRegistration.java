/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;

import org.jboss.as.controller.PathElement;

/**
 * Registration for subsystem specific operation transformers.
 *
 * @author Emanuel Muckenhuber
 */
public interface TransformersSubRegistration {

    String[] COMMON_OPERATIONS = { ADD, REMOVE };

    /**
     * Register a sub resource.
     *
     * @param element the path element
     * @return the sub registration
     */
    TransformersSubRegistration registerSubResource(PathElement element);

    /**
     * Register a sub resource. If discardByDefault is set to {@code true}, both operations and resource transformations
     * are going to discard operations addressed to this resource.
     *
     * @param element the path element
     * @param discardByDefault don't forward operations by default
     * @return the sub registration
     */
    TransformersSubRegistration registerSubResource(PathElement element, boolean discardByDefault);

    /**
     * register a sub resource.
     *
     * @param element the path element
     * @param resourceTransformer the resource transformer
     * @return the transformers sub registration
     */
    TransformersSubRegistration registerSubResource(PathElement element, ResourceTransformer resourceTransformer);

    /**
     * Register a sub resource.
     *
     * @param element the path element
     * @param operationTransformer the default operation transformer
     * @return the sub registration
     */
    TransformersSubRegistration registerSubResource(PathElement element, OperationTransformer operationTransformer);

    /**
     * Register a sub resource.
     *
     * @param element the path element
     * @param resourceTransformer the resource transformer
     * @param operationTransformer the default operation transformer
     * @return the transformers sub registration
     */
    TransformersSubRegistration registerSubResource(PathElement element, ResourceTransformer resourceTransformer, OperationTransformer operationTransformer);

    /**
     * Register a sub resource.
     *
     * @param element the path element
     * @param pathAddressTransformer the path transformation
     * @param resourceTransformer the resource transformer
     * @param operationTransformer the default operation transformer
     * @return the transformers sub registration
     */
    TransformersSubRegistration registerSubResource(PathElement element, PathAddressTransformer pathAddressTransformer, ResourceTransformer resourceTransformer, OperationTransformer operationTransformer);

    /**
     * Register a sub resource.
     *
     * @param element the path element
     * @param pathAddressTransformer the path transformation
     * @param resourceTransformer the resource transformer
     * @param operationTransformer the default operation transformer
     * @param inherited {@code true} to make the default operation transformer inherited
     * @param placeholder {@code true} if the transformers are placeholders and are responsible for resolving the children
     * @return the transformers sub registration
     */
    TransformersSubRegistration registerSubResource(PathElement element, PathAddressTransformer pathAddressTransformer, ResourceTransformer resourceTransformer, OperationTransformer operationTransformer, boolean inherited, boolean placeholder);

    /**
     * Register a sub resource.
     *
     * @param element the path element
     * @param transformer the resource and operation transformer
     * @return the transformers sub registration
     */
    TransformersSubRegistration registerSubResource(PathElement element, CombinedTransformer transformer);

    /**
     * Don't forward and just discard the operation.
     *
     * @param operationNames the operation names
     */
    void discardOperations(String... operationNames);

    /**
     * Register an operation transformer.
     *
     * @param operationName the operation name
     * @param transformer the operation transformer
     */
    void registerOperationTransformer(String operationName, OperationTransformer transformer);

}
