/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.Resource;

/**
 * The resource transformation context.
 *
* @author Emanuel Muckenhuber
*/
public interface ResourceTransformationContext extends TransformationContext, Transformers.ResourceIgnoredTransformationRegistry {

    /**
     * Add a resource.
     *
     * @param relativeAddress the relative address
     * @param resource the resource model to add
     * @return the resource transformation context
     */
    ResourceTransformationContext addTransformedResource(PathAddress relativeAddress, Resource resource);

    /**
     * Add a resource from the root of the model.
     *
     * @param absoluteAddress the absolute address
     * @param resource the resource model to add
     * @return the resource transformation context
     */
    ResourceTransformationContext addTransformedResourceFromRoot(PathAddress absoluteAddress, Resource resource);

    /**
     * Add a resource recursively including it's children.
     *
     * @param relativeAddress the relative address
     * @param resource the resource to add
     */
    void addTransformedRecursiveResource(PathAddress relativeAddress, Resource resource);

    /**
     * Process all children of a given resource.
     *
     * @param resource the resource
     * @throws OperationFailedException
     */
    void processChildren(Resource resource) throws OperationFailedException;

    /**
     * Process a child.
     *
     * @param element the path element
     * @param child the child
     * @throws  OperationFailedException
     */
    void processChild(PathElement element, Resource child) throws OperationFailedException;

    /**
     * Read a resource from the transformed model.
     *
     * NOTE: this is going to already use the path transformed address {@linkplain PathAddressTransformer}.
     *
     * @param address the relative address
     * @return the resource
     */
    Resource readTransformedResource(PathAddress address);

    /**
     * Get the transformed root.
     *
     * @return the transformed root
     */
    Resource getTransformedRoot();
}
