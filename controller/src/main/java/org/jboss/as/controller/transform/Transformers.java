/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
import org.jboss.as.version.Stability;
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
     * <strong>Only for use by test frameworks.</strong> Transforms an operation.
     *
     * @param context contextual information about the transformation
     * @param operation the operation to transform
     * @return the transformed operation
     * @throws OperationFailedException
     */
    OperationTransformer.TransformedOperation transformOperation(TransformationContext context, ModelNode operation) throws OperationFailedException;

    /**
     * Transform an operation.
     *
     * @param transformationInputs standard inputs into a transformation process. Cannot be {@code null}
     * @param operation the operation to transform. Cannot be {@code null}
     * @return the transformed operation. Will not be {@code null}
     * @throws OperationFailedException
     */
    OperationTransformer.TransformedOperation transformOperation(TransformationInputs transformationInputs, ModelNode operation) throws OperationFailedException;

    /**
     * <strong>Only for use by test frameworks.</strong>. Transforms the given resource.
     *
     * @param context  contextual information about the transformation
     * @param resource to transform
     * @return transformed resource, or same if no transformation was needed
     * @throws OperationFailedException
     */
    Resource transformResource(ResourceTransformationContext context, Resource resource) throws OperationFailedException;

    /**
     * Transform a given root resource, including children. The given {@code resource} must represent the root of
     * HC's full resource tree but need not include all children, if the caller is not interested in transforming
     * the excluded children.
     *
     * @param transformationInputs standard inputs into a transformation process. Cannot be {@code null}
     * @param resource the root resource. Cannot be {@code null}
     * @return the transformed resource. Will not be {@code null}
     * @throws OperationFailedException
     */
    Resource transformRootResource(TransformationInputs transformationInputs, Resource resource) throws OperationFailedException;

    /**
     * Transform a given resource, including children, removing resources that the given {@code ignoredTransformationRegistry}
     * indicates are being ignored by the target process. The given {@code resource} must represent the root of
     * HC's full resource tree but need not include all children, if the caller is not interested in transforming
     * the excluded children.
     *
     * @param transformationInputs standard inputs to a transformation. Cannot be {@code null}
     * @param resource the resource to be transformed (including children)
     * @param ignoredTransformationRegistry provider of information on what addresses are being ignored by the target process
     * @return the transformed resource
     *
     * @throws OperationFailedException
     */
    Resource transformRootResource(TransformationInputs transformationInputs, Resource resource, ResourceIgnoredTransformationRegistry ignoredTransformationRegistry) throws OperationFailedException;

    /**
     * Standard inputs into a transformation process. These are derived from an {@link OperationContext}
     * at the time they are created but this class does not use the operation context thereafter, making
     * it safe for use by other threads not associated with the operation context.
     */
    class TransformationInputs {

        private static final OperationContext.AttachmentKey<TransformationInputs> KEY = OperationContext.AttachmentKey.create(TransformationInputs.class);

        private final Resource originalModel;
        private final ImmutableManagementResourceRegistration registration;
        private final ProcessType processType;
        private final RunningMode runningMode;
        private final TransformerOperationAttachment transformerOperationAttachment;

        /**
         * Obtains a set of {@code TransformationInputs} from the given operation context. If the
         * context's {@link OperationContext#getCurrentStage() current stage} is
         * {@link org.jboss.as.controller.OperationContext.Stage#DOMAIN} any inputs cached with
         * the context as an attachment will be used, and if none are cached, then the created inputs
         * will be cached.
         *
         * @param context the operation context. Cannot be {@code null}
         * @return the inputs. Will not be {@code null}
         */
        public static TransformationInputs getOrCreate(OperationContext context) {
            TransformationInputs result;
            if (context.getCurrentStage() == OperationContext.Stage.DOMAIN) {
                // Stage.DOMAIN means the model is not going to change, so we can safely cache.
                // We want to cache because reading the entire model is expensive, so we don't
                // want to do it for every process we need to interact with in a domain roll out.
                result = context.getAttachment(KEY);
                if (result == null) {
                    result = new TransformationInputs(context);
                    context.attach(KEY, result);
                }
            } else {
                result = new TransformationInputs(context);
            }
            return result;
        }

        /**
         * Creates a new {@code TransformationInputs} from the given operation context.
         * @param context  the operation context. Cannot be {@code null}
         */
        public TransformationInputs(OperationContext context) {
            this.originalModel = context.readResourceFromRoot(PathAddress.EMPTY_ADDRESS, true);
            this.registration = context.getRootResourceRegistration();
            this.processType = context.getProcessType();
            this.runningMode = context.getRunningMode();
            this.transformerOperationAttachment = context.getAttachment(TransformerOperationAttachment.KEY);
        }

        /**
         * Gets a copy of the full resource tree as it existed at the time this object was created.
         *
         * @return the resource tree. Will not be {@code null}
         */
        public Resource getRootResource() {
            return originalModel;
        }

        /**
         * Gets full the {@link ImmutableManagementResourceRegistration resource registration} tree.
         * @return the resource registration tree. Will not be {@code null}
         */
        public ImmutableManagementResourceRegistration getRootRegistration() {
            return registration;
        }

        /**
         * Gets the type of this process.
         * @return the process type. Will not be {@code null}
         */
        public ProcessType getProcessType() {
            return processType;
        }

        /**
         * Gets the process' running mode at the time this object was created.
         * @return the running mode. Will not be {@code null}
         */
        public RunningMode getRunningMode() {
            return runningMode;
        }

        /**
         * Gets any {@link TransformerOperationAttachment} that was attached to the {@link OperationContext}
         * at the time this object was created.
         * @return the attachment, or {@code null} if there was none.
         */
        public TransformerOperationAttachment getTransformerOperationAttachment() {
            return transformerOperationAttachment;
        }
    }

    /**
     * Convenience factory for unit tests, and default internal implementations
     */
    class Factory {
        private Factory() {
        }

        /**
         * Returns a transformers object appropriate for the given target process.
         * @param target the transformation target
         * @return the transformers instance. Will not be {@code null}
         */
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
         * @return the created context Will not be {@code null}
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
         * @return the created context Will not be {@code null}
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
         * @return the transformers instance. Will not be {@code null}
         */
        @Deprecated(forRemoval = true, since = "27.0.0")
        public static Transformers createLocal() {
            return new TransformersImpl(TransformationTargetImpl.createLocal());
        }

        /**
         * Create a local transformer, which will use the default transformation rules, however still respect the
         * ignored resource transformation.
         * @param stability the stability level of the target host
         * @return the transformers instance. Will not be {@code null}
         */
        public static Transformers createLocal(Stability stability) {
            return new TransformersImpl(TransformationTargetImpl.createLocal(stability));
        }
    }

    /** Provides information on whether a target process is ignoring particular resource addresses. */
    @FunctionalInterface
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

    /**
     * A default {@link org.jboss.as.controller.transform.Transformers.ResourceIgnoredTransformationRegistry}
     * that says that no addresses are being ignored.
     */
    ResourceIgnoredTransformationRegistry DEFAULT = new ResourceIgnoredTransformationRegistry() {

        /**
         * Always returns {@code false}
         *
         * {@inheritDoc}
         *
         * @return {@code false}, always
         */
        @Override
        public boolean isResourceTransformationIgnored(PathAddress address) {
            return false;
        }
    };

    /** Provides information on whether a target process is excluded from receiving operations for a particular resource addresses. */
    @FunctionalInterface
    interface OperationExcludedTransformationRegistry {

        /**
         * Gets whether an operation with the given {@code address} should be excluded from normal
         * {@link TransformationTarget#resolveTransformer(TransformationContext, PathAddress, String)} transformation}
         * and instead simply {@link OperationTransformer#DISCARD discarded}.
         *
         * @param address the operation address. Cannot be {@code null}
         * @param operationName the name of the operation
         * @return {@code true} if the operation should be excluded from normal operation transformation
         */
        boolean isOperationExcluded(final PathAddress address, String operationName);


        /**
         * A default {@link OperationExcludedTransformationRegistry}
         * that says that no addresses are being excluded.
         */
        OperationExcludedTransformationRegistry DEFAULT = new OperationExcludedTransformationRegistry() {

            /**
             * Always returns {@code false}
             *
             * {@inheritDoc}
             *
             * @return {@code false}, always
             */
            @Override
            public boolean isOperationExcluded(PathAddress address, String operationName) {
                return false;
            }
        };

    }
}
