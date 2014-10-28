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

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * Transformers API for manipulating transformation operations between different versions of application server
 *
 * @author Emanuel Muckenhuber
 * @author Tomaz Cerar
 * @since 7.1.2
 */
public interface Transformers {

    /**
     * Get information about the target.
     *
     * @return the target
     */
    TransformationTarget getTarget();

    /**
     * Transform an operation.
     *
     * @param context the transformation context
     * @param operation the operation to transform
     * @return the transformed operation
     * @throws OperationFailedException
     */
    OperationTransformer.TransformedOperation transformOperation(TransformationContext context, ModelNode operation) throws OperationFailedException;

    /**
     * Transform an operation.
     *
     * @param operationContext the operation context
     * @param operation the operation to transform
     * @return the transformed operation
     * @throws OperationFailedException
     */
    OperationTransformer.TransformedOperation transformOperation(OperationContext operationContext, ModelNode operation) throws OperationFailedException;

    /**
     * Transform given resource at given context
     *
     * @param context  from where resource originates
     * @param resource to transform
     * @return transformed resource, or same if no transformation was needed
     * @throws OperationFailedException
     */
    Resource transformResource(ResourceTransformationContext context, Resource resource) throws OperationFailedException;

    /**
     * Transform a given root resource.
     *
     * @param operationContext the operation context
     * @param resource the root resource
     * @return the transformed resource
     * @throws OperationFailedException
     */
    Resource transformRootResource(OperationContext operationContext, Resource resource) throws OperationFailedException;

    Resource transformRootResource(OperationContext operationContext, Resource resource, ResourceIgnoredTransformationRegistry ignoredTransformationRegistry) throws OperationFailedException;

    /**
     * Convenience factory for unit tests, and default internal implementations
     */
    public static class Factory {
        private Factory() {
        }

        public static Transformers create(final TransformationTarget target) {
            return new TransformersImpl(target);
        }

        /**
         * Creates a ResourceTransformationContext
         *
         * @param target the transformation target
         * @param model the model
         * @param registration the resource registration
         * @param resolver the expression resolver
         * @param runningMode the server running mode
         * @param type the process type
         * @param attachment attachments propagated from the operation context to the created transformer context.
         *                   This may be {@code null}. In a non-test scenario, this will be added by operation handlers
         *                   triggering the transformation, but for tests this needs to be hard-coded. Tests will need to
         *                   ensure themselves that the relevant attachments get set.
         *
         * @return the created context
         */
        public static ResourceTransformationContext create(TransformationTarget target, Resource model,
                                                           ImmutableManagementResourceRegistration registration, ExpressionResolver resolver,
                                                           RunningMode runningMode, ProcessType type, TransformerOperationAttachment attachment) {
            return ResourceTransformationContextImpl.create(target, model, registration, runningMode, type, attachment, DEFAULT);
        }

        /**
         * Creates a ResourceTransformationContext
         *
         * @param target the transformation target
         * @param model the model
         * @param registration the resource registration
         * @param resolver the expression resolver
         * @param runningMode the server running mode
         * @param type the process type
         * @param attachment attachments propagated from the operation context to the created transformer context.
         *                   This may be {@code null}. In a non-test scenario, this will be added by operation handlers
         *                   triggering the transformation, but for tests this needs to be hard-coded. Tests will need to
         *                   ensure themselves that the relevant attachments get set.
         *
         * @return the created context
         */
        public static ResourceTransformationContext create(TransformationTarget target, Resource model,
                                                           ImmutableManagementResourceRegistration registration, ExpressionResolver resolver,
                                                           RunningMode runningMode, ProcessType type, TransformerOperationAttachment attachment,
                                                           Transformers.ResourceIgnoredTransformationRegistry ignoredTransformationRegistry) {
            return ResourceTransformationContextImpl.create(target, model, registration, runningMode, type, attachment, ignoredTransformationRegistry);
        }

        /**
         * Create a local transformer, which will use the default transformation rules, however still respect the
         * ignored resource transformation.
         *
         * @return
         */
        public static Transformers createLocal() {
            return new TransformersImpl(TransformationTargetImpl.createLocal());
        }

    }

    interface ResourceIgnoredTransformationRegistry {

        /**
         * Gets whether a resource with the given {@code address} should be excluded from
         * {@link TransformationTarget#resolveTransformer(ResourceTransformationContext, org.jboss.as.controller.PathAddress) resource transformation}.
         *
         * @param address the resource address. Cannot be {@code null}
         * @return {@code true} if the resource should be excluded from resource transformation
         */
        boolean isResourceTransformationIgnored(final PathAddress address);

    }

    ResourceIgnoredTransformationRegistry DEFAULT = new ResourceIgnoredTransformationRegistry() {
        @Override
        public boolean isResourceTransformationIgnored(PathAddress address) {
            return false;
        }
    };

}
