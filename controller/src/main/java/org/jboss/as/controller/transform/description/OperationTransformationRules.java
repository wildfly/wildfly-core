/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.OperationRejectionPolicy;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.dmr.ModelNode;

import java.util.Map;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;

/**
 * Custom operation transformation rules.
 *
 * @author Emanuel Muckenhuber
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class OperationTransformationRules {

    static OperationTransformer createWriteOperation(final Map<String, AttributeTransformationDescription> attributeTransformations) {
        return new DefaultTransformer(new WriteAttributeRule(attributeTransformations));
    }

    static OperationTransformer createUndefinedOperation(final Map<String, AttributeTransformationDescription> attributeTransformations) {
        return new DefaultTransformer(new UndefineAttributeRule(attributeTransformations));
    }

    static class DefaultTransformer implements OperationTransformer {

        private final TransformationRule rule;
        DefaultTransformer(TransformationRule rule) {
            this.rule = rule;
        }

        @Override
        public TransformedOperation transformOperation(TransformationContext context, PathAddress address, ModelNode operation) throws OperationFailedException {
            final TransformationRule.ChainedOperationContext ctx = new TransformationRule.ChainedOperationContext(context) {
                @Override
                void invokeNext(TransformedOperation transformedOperation) throws OperationFailedException {
                    recordTransformedOperation(transformedOperation);
                }
            };
            rule.transformOperation(operation, address, ctx);
            return ctx.createOp();
        }
    }

    static class WriteAttributeRule extends TransformationRule {

        private final Map<String, AttributeTransformationDescription> attributeTransformations;
        WriteAttributeRule(Map<String, AttributeTransformationDescription> attributeTransformations) {
            this.attributeTransformations = attributeTransformations;
        }

        @Override
        void transformOperation(final ModelNode operation, final PathAddress address, final ChainedOperationContext context) throws OperationFailedException {
            final String attributeName = operation.require(ModelDescriptionConstants.NAME).asString();
            final AttributeTransformationDescription description = attributeTransformations.get(attributeName);
            if(description == null) {
                context.invokeNext(operation);
                return;
            }
            final TransformationContext ctx = context.getContext();
            final ModelNode attributeValue = operation.get(ModelDescriptionConstants.VALUE);
            //discard what can be discarded
            if (description.shouldDiscard(address, attributeValue, operation, context)) {
                context.recordTransformedOperation(OperationTransformer.DISCARD.transformOperation(ctx, address, operation));
                return;
            }
            //Make sure that context.readResourceXXX() returns an unmodifiable Resource
            context.setImmutableResource(true);
            try {
                //Check the rest of the model can be transformed
                final RejectedAttributesLogContext rejectedAttributes = new RejectedAttributesLogContext(context, address, TransformationRule.cloneAndProtect(operation));
                description.rejectAttributes(rejectedAttributes, attributeValue);
                final OperationRejectionPolicy policy;
                if(rejectedAttributes.hasRejections()) {

                    policy = new OperationRejectionPolicy() {
                        @Override
                        public boolean rejectOperation(ModelNode preparedResult) {
                            return true;
                        }

                        @Override
                        public String getFailureDescription() {
                            return rejectedAttributes.getOperationRejectDescription();
                        }
                    };
                } else {
                    policy = OperationTransformer.DEFAULT_REJECTION_POLICY;
                }

                //Now transform the value
                description.convertValue(address, attributeValue, TransformationRule.cloneAndProtect(operation), context);

                //Change the name
                String newName = description.getNewName();
                if (newName != null) {
                    operation.get(NAME).set(newName);
                }

                context.invokeNext(new OperationTransformer.TransformedOperation(operation, policy, OperationResultTransformer.ORIGINAL_RESULT));
            } finally {
                context.setImmutableResource(false);
            }

        }

        @Override
        void transformResource(Resource resource, PathAddress address, ChainedResourceContext context) throws OperationFailedException {
            //
        }
    }

    static final ModelNode UNDEFINED = new ModelNode();

    static class UndefineAttributeRule extends TransformationRule {

        private final Map<String, AttributeTransformationDescription> attributeTransformations;
        UndefineAttributeRule(Map<String, AttributeTransformationDescription> attributeTransformations) {
            this.attributeTransformations = attributeTransformations;
        }

        @Override
        void transformOperation(ModelNode operation, PathAddress address, ChainedOperationContext context) throws OperationFailedException {
            final String attributeName = operation.require(ModelDescriptionConstants.NAME).asString();
            final AttributeTransformationDescription description = attributeTransformations.get(attributeName);
            if(description == null) {
                context.invokeNext(operation);
                return;
            }
            final ModelNode originalModel = operation.clone();
            //Make sure that context.readResourceXXX() returns an unmodifiable Resource
            context.setImmutableResource(true);
            try {
                //discard what can be discarded
                if (description.shouldDiscard(address, UNDEFINED, originalModel, context)) {
                    context.invokeNext(OperationTransformer.DISCARD.transformOperation(context.getContext(), address, operation));
                } else {
                    context.invokeNext(operation);
                }
            } finally {
                context.setImmutableResource(false);
            }
        }

        @Override
        void transformResource(Resource resource, PathAddress address, ChainedResourceContext context) throws OperationFailedException {
            //
        }
    }

}
