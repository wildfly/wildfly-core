/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.OperationRejectionPolicy;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 */
class AttributeTransformationRule extends TransformationRule {

    private final Map<String, AttributeTransformationDescription> descriptions;
    AttributeTransformationRule(Map<String, AttributeTransformationDescription> descriptions) {
        this.descriptions = descriptions;
    }

    @Override
    void transformOperation(final ModelNode operation, PathAddress address, ChainedOperationContext context) throws OperationFailedException {
        final ModelNode transformed = operation.clone();
        final RejectedAttributesLogContext rejectedAttributes = new RejectedAttributesLogContext(context, address, operation);

        doTransform(address, transformed, operation, context, rejectedAttributes);

        final OperationRejectionPolicy policy;
        final OperationResultTransformer resultTransformer;
        if (!rejectedAttributes.hasRejections()) {
            policy = OperationTransformer.DEFAULT_REJECTION_POLICY;
            resultTransformer = OperationResultTransformer.ORIGINAL_RESULT;
        } else {
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
            resultTransformer = new OperationResultTransformer() {
                @Override
                public ModelNode transformResult(ModelNode result) {
                    ModelNode res = result;
                    if (!result.hasDefined(OUTCOME) || SUCCESS.equals(result.get(OUTCOME).asString())) {
                        res = result.clone();
                        res.get(OUTCOME).set(FAILED);
                        res.get(FAILURE_DESCRIPTION).set(policy.getFailureDescription());
                    }
                    return res;
                }
            };
        }

        context.invokeNext(new OperationTransformer.TransformedOperation(transformed, policy, resultTransformer));
    }

    @Override
    void transformResource(final Resource resource, final PathAddress address, final ChainedResourceContext context) throws OperationFailedException {
        final ModelNode model = resource.getModel();
        RejectedAttributesLogContext rejectedAttributes = new RejectedAttributesLogContext(context, address, null);
        doTransform(address, model, null, context, rejectedAttributes);
        if (rejectedAttributes.hasRejections()) {
            rejectedAttributes.errorOrWarnOnResourceTransformation();
        }
        context.invokeNext(resource);
    }

    private void doTransform(PathAddress address, ModelNode modelOrOp, ModelNode operation, AbstractChainedContext context, RejectedAttributesLogContext rejectedAttributes) {
        Map<String, String> renames = new HashMap<String, String>();
        Map<String, ModelNode> adds = new HashMap<String, ModelNode>();
        Set<String> newAttributes = new HashSet<String>();
        Set<String> discardedAttributes = new HashSet<String>();

        //Make sure that context.readResourceXXX() returns an unmodifiable Resource
        context.setImmutableResource(true);
        try {
            //Initial setup and discard
            for(final Map.Entry<String, AttributeTransformationDescription> entry : descriptions.entrySet()) {
                final String attributeName = entry.getKey();
                final boolean isNewAttribute = !modelOrOp.has(attributeName);
                final ModelNode attributeValue = modelOrOp.get(attributeName);

                if (isNewAttribute) {
                    newAttributes.add(attributeName);
                }

                AttributeTransformationDescription description = entry.getValue();

                //discard what can be discarded
                if (description.shouldDiscard(address, TransformationRule.cloneAndProtect(attributeValue), operation, context)) {
                    modelOrOp.remove(attributeName);
                    discardedAttributes.add(attributeName);
                }
                String newName = description.getNewName();
                if (newName != null) {
                    renames.put(attributeName, newName);
                }
            }

            //Check rejections (unless it is a remove operation, in which case we just remove)
            if (operation == null || (!operation.get(ModelDescriptionConstants.OP).asString().equals(ModelDescriptionConstants.REMOVE) && !
                    operation.get(ModelDescriptionConstants.OP).asString().equals(ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION))) {
                for(final Map.Entry<String, AttributeTransformationDescription> entry : descriptions.entrySet()) {
                    final String attributeName = entry.getKey();
                    if (!discardedAttributes.contains(attributeName)) {
                        final ModelNode attributeValue = modelOrOp.get(attributeName);
                        AttributeTransformationDescription description = entry.getValue();

                        //Check the rest of the model can be transformed
                        description.rejectAttributes(rejectedAttributes, TransformationRule.cloneAndProtect(attributeValue));
                    }
                }
            }
            //Do conversions
            for(final Map.Entry<String, AttributeTransformationDescription> entry : descriptions.entrySet()) {
                final String attributeName = entry.getKey();
                if (!discardedAttributes.contains(attributeName)) {
                    final ModelNode attributeValue = modelOrOp.get(attributeName);
                    AttributeTransformationDescription description = entry.getValue();

                    description.convertValue(address, attributeValue, operation, context);
                    if (!attributeValue.isDefined()) {
                        modelOrOp.remove(attributeName);
                    } else if (newAttributes.contains(attributeName)) {
                        adds.put(attributeName, attributeValue);
                    }
                }
            }

        } finally {
            context.setImmutableResource(false);
        }

        if (renames.size() > 0) {
            for (Map.Entry<String, String> entry : renames.entrySet()) {
                if (modelOrOp.has(entry.getKey())) {
                    ModelNode model = modelOrOp.remove(entry.getKey());
                    if (model.isDefined()) {
                        modelOrOp.get(entry.getValue()).set(model);
                    }
                }
            }
        }
        if (adds.size() > 0) {
            for (Map.Entry<String, ModelNode> entry : adds.entrySet()) {
                modelOrOp.get(entry.getKey()).set(entry.getValue());
            }
        }
    }
}
