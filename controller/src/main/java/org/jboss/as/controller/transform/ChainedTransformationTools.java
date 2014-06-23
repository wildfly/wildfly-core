/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.controller.transform;

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
     * @param extraResolver the extra resolver to use to resolve the placeholder's children for the first model version delta in the chain
     * @return a new {@code ResourceTransformationContext} instance using the extra resolver
     */
    public static ResourceTransformationContext initialiseChain(ResourceTransformationContext context, PlaceholderResolver extraResolver) {
        assert context instanceof ResourceTransformationContextImpl : "Wrong type of context";
        ResourceTransformationContextImpl ctx = (ResourceTransformationContextImpl)context;
        return ctx.copy(extraResolver);
    }

    /**
     * Call when transforming a new model version delta. This will copy the {@link ResourceTransformationContext} instance, using the extra resolver
     * to resolve the children of the placeholder resource.
     *
     * @param context the context to copy. It should be at a chained placeholder
     * @param extraResolver the extra resolver to use to resolve the placeholder's children for the model version delta we are transforming
     * @return a new {@code ResourceTransformationContext} instance using the extra resolver
     */
    public static ResourceTransformationContext nextInChain(ResourceTransformationContext context, PlaceholderResolver extraResolver) {
        assert context instanceof ResourceTransformationContextImpl : "Wrong type of context";
        ResourceTransformationContextImpl ctx = (ResourceTransformationContextImpl)context;
        ResourceTransformationContext copy = ctx.copyAndReplaceOriginalModel(extraResolver);

        return copy;
    }
}
