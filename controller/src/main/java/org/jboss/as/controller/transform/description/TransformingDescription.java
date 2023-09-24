/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.transform.OperationRejectionPolicy;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.PathAddressTransformer;
import org.jboss.as.controller.transform.ResourceTransformationContext;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.dmr.ModelNode;

/**
 * @author Emanuel Muckenhuber
 */
class TransformingDescription extends AbstractDescription implements TransformationDescription, ResourceTransformer, OperationTransformer {

    private final DiscardPolicy discardPolicy;
    private final List<TransformationDescription> children;
    private final Map<String, AttributeTransformationDescription> attributeTransformations;
    private final List<TransformationRule> rules = Collections.emptyList();
    private final Map<String, OperationTransformer> operationTransformers;
    private final Set<String> discardedOperations;
    private final ResourceTransformer resourceTransformer;
    private final DynamicDiscardPolicy dynamicDiscardPolicy;

    protected TransformingDescription(final PathElement pathElement, final PathAddressTransformer pathAddressTransformer,
                                   final DiscardPolicy discardPolicy, final boolean inherited,
                                   final ResourceTransformer resourceTransformer,
                                   final Map<String, AttributeTransformationDescription> attributeTransformations,
                                   final Map<String, OperationTransformer> operations,
                                   final List<TransformationDescription> children,
                                   final Set<String> discardedOperations,
                                   final DynamicDiscardPolicy dynamicDiscardPolicy) {
        super(pathElement, pathAddressTransformer, inherited);
        this.children = children;
        this.discardPolicy = discardPolicy;
        this.resourceTransformer = resourceTransformer;
        this.attributeTransformations = attributeTransformations;
        this.discardedOperations = discardedOperations;
        this.operationTransformers = operations;
        this.dynamicDiscardPolicy = dynamicDiscardPolicy;
    }
    @Override
    public OperationTransformer getOperationTransformer() {
        return this;
    }

    @Override
    public ResourceTransformer getResourceTransformer() {
        return this;
    }

    @Override
    public Map<String, OperationTransformer> getOperationTransformers() {
        return Collections.unmodifiableMap(operationTransformers);
    }

    @Override
    public List<TransformationDescription> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public OperationTransformer.TransformedOperation transformOperation(final TransformationContext ctx, final PathAddress address, final ModelNode operation) throws OperationFailedException {
        // See whether the operation should be rejected or not
        final DiscardPolicy discardPolicy = determineDiscardPolicy(ctx, address);
        switch (discardPolicy) {
            case REJECT_AND_WARN:
                // Just execute the original operation to see that it failed
                return new TransformedOperation(operation, new OperationRejectionPolicy() {
                    @Override
                    public boolean rejectOperation(ModelNode preparedResult) {
                        return true;
                    }

                    @Override
                    public String getFailureDescription() {
                        return ControllerLogger.ROOT_LOGGER.rejectResourceOperationTransformation(address, operation);
                    }
                }, OperationResultTransformer.ORIGINAL_RESULT);
            case DISCARD_AND_WARN:
            case SILENT:
                return OperationTransformer.DISCARD.transformOperation(ctx, address, operation);
        }
        final Iterator<TransformationRule> iterator = rules.iterator();
        final TransformationRule.ChainedOperationContext context = new TransformationRule.ChainedOperationContext(ctx) {

            @Override
            void invokeNext(OperationTransformer.TransformedOperation transformedOperation) throws OperationFailedException {
                recordTransformedOperation(transformedOperation);
                if(iterator.hasNext()) {
                    final TransformationRule next = iterator.next();
                    // TODO hmm, do we need to change the address?
                    next.transformOperation(transformedOperation.getTransformedOperation(), address, this);
                }
            }
        };
        // Kick off the chain
        final TransformationRule first = new AttributeTransformationRule(attributeTransformations);
        first.transformOperation(operation, address, context);
        // Create the composite operation result
        return context.createOp();
    }

    @Override
    public void transformResource(final ResourceTransformationContext ctx, final PathAddress address, final Resource original) throws OperationFailedException {
        final DiscardPolicy discardPolicy = determineDiscardPolicy(ctx, address);

        switch (discardPolicy) {
            case REJECT_AND_WARN:
                ctx.getLogger().logRejectedResourceWarning(address, null);
                return;
            case DISCARD_AND_WARN:
                // don't return yet, just log a warning first and then discard
                ctx.getLogger().logDiscardedResourceWarning(address, ctx.getTarget().getHostName());
            case SILENT:
                ResourceTransformer.DISCARD.transformResource(ctx, address, original);
                return;
        }
        final Iterator<TransformationRule> iterator = rules.iterator();
        final TransformationRule.ChainedResourceContext context = new TransformationRule.ChainedResourceContext(ctx) {
            @Override
            void invokeNext(final Resource resource) throws OperationFailedException {
                if(iterator.hasNext()) {
                    final TransformationRule next = iterator.next();
                    next.transformResource(resource, address, this);
                } else {
                    resourceTransformer.transformResource(ctx, address, resource);
                }
            }
        };
        // Kick off the chain
        final TransformationRule rule = new AttributeTransformationRule(attributeTransformations);
        rule.transformResource(original, address, context);
    }

    public Set<String> getDiscardedOperations() {
        return discardedOperations;
    }

    @Override
    public boolean isPlaceHolder() {
        return false;
    }

    private DiscardPolicy determineDiscardPolicy(TransformationContext ctx, PathAddress address) {
        //Check the discard policy
        final DiscardPolicy discardPolicy;
        if (dynamicDiscardPolicy == null) {
            discardPolicy = this.discardPolicy;
        } else {
            //Use the provided resource checker
            discardPolicy = dynamicDiscardPolicy.checkResource(ctx, address);
        }
        return discardPolicy;
    }
}
