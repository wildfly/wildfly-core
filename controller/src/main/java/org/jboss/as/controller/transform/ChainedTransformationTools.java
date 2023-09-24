/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.transform;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.OperationTransformerRegistry.PlaceholderResolver;



/**
 * Tools to interact with the package protected {@link ResourceTransformationContext} implementation classes.
 * Used for chained transformers
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ChainedTransformationTools {

    /**
     * Call when starting a new chain of model versions. This will copy the {@link ResourceTransformationContext} instance, using the extra resolver
     * to resolve the children of the placeholder resource.
     *
     * @param context the context to copy. It should be at a chained placeholder
     * @param placeholderResolver the extra resolver to use to resolve the placeholder's children for the first model version delta in the chain
     * @return a new {@code ResourceTransformationContext} instance using the extra resolver
     */
    public static ResourceTransformationContext initialiseChain(ResourceTransformationContext context, PlaceholderResolver placeholderResolver) {
        assert context instanceof ResourceTransformationContextImpl : "Wrong type of context";
        ResourceTransformationContextImpl ctx = (ResourceTransformationContextImpl)context;
        return ctx.copy(placeholderResolver);
    }

    /**
     * Call when transforming a new model version delta for a resource. This will copy the {@link ResourceTransformationContext} instance, using the extra resolver
     * to resolve the children of the placeholder resource.
     *
     * @param context the context to copy. It should be at a chained placeholder
     * @param placeholderResolver the extra resolver to use to resolve the placeholder's children for the model version delta we are transforming
     * @return a new {@code ResourceTransformationContext} instance using the extra resolver
     */
    public static ResourceTransformationContext nextInChainResource(ResourceTransformationContext context, PlaceholderResolver placeholderResolver) {
        assert context instanceof ResourceTransformationContextImpl : "Wrong type of context";
        ResourceTransformationContextImpl ctx = (ResourceTransformationContextImpl)context;
        ResourceTransformationContext copy = ctx.copyAndReplaceOriginalModel(placeholderResolver);

        return copy;
    }

    /**
     * Call when transforming a new model version delta for an operation. This will copy the {@link ResourceTransformationContext} instance, using the extra resolver
     * to resolve the children of the placeholder resource.
     *
     * @param context the context to copy. It should be at a chained placeholder
     * @param placeholderResolver the extra resolver to use to resolve the placeholder's children for the model version delta we are transforming
     * @return a new {@code ResourceTransformationContext} instance using the extra resolver
     */
    public static ResourceTransformationContext nextInChainOperation(ResourceTransformationContext context, PlaceholderResolver placeholderResolver) {
        assert context instanceof ResourceTransformationContextImpl : "Wrong type of context";
        ResourceTransformationContextImpl ctx = (ResourceTransformationContextImpl)context;
        ResourceTransformationContext copy = ctx.copy(placeholderResolver);

        return copy;
    }

    /**
     * Transform a path address.
     *
     * @param original the path address to be transformed
     * @param target the transformation target
     * @return the transformed path address
     */
    public static PathAddress transformAddress(final PathAddress original, final TransformationTarget target) {
        return TransformersImpl.transformAddress(original, target);
    }
}
