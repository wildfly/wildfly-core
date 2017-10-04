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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.COMPOSITE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.IGNORED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.STEPS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.AliasEntry;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.dmr.ModelNode;

/**
 * Operation transformer responsible for handling a composite operation.
 *
 * @author Emanuel Muckenhuber
 */
class CompositeOperationTransformer implements OperationTransformer {

    private static final ModelNode SUCCESSFUL = new ModelNode();
    static {
        SUCCESSFUL.get(OUTCOME).set(SUCCESS);
        SUCCESSFUL.get(RESULT);
        SUCCESSFUL.protect();
    }

    @Override
    public TransformedOperation transformOperation(final TransformationContext context, final PathAddress address, final ModelNode operation) throws OperationFailedException {
        return transformOperation(context, operation);
    }

    private TransformedOperation transformOperation(final TransformationContext context, final ModelNode operation) throws OperationFailedException {
        final ModelNode composite = operation.clone();
        composite.get(STEPS).setEmptyList();
        final TransformationTarget target = context.getTarget();
        final List<Step> steps = new ArrayList<Step>();
        int stepIdx = 0, resultIdx  = 0;
        for(final ModelNode step : operation.require(STEPS).asList()) {
            stepIdx++;
            final String operationName = step.require(OP).asString();
            final PathAddress stepAddress = step.hasDefined(OP_ADDR) ? PathAddress.pathAddress(step.require(OP_ADDR)) : PathAddress.EMPTY_ADDRESS;
            final TransformedOperation result;
            if(stepAddress.size() == 0 && COMPOSITE.equals(operationName)) {
                // Process nested steps directly
                result = transformOperation(context, step);
            } else {
                //If this is an alias, get the real address before transforming
                ImmutableManagementResourceRegistration reg = context.getResourceRegistrationFromRoot(stepAddress);
                final PathAddress useAddress;
                if (reg != null && reg.isAlias()) {
                    useAddress = reg.getAliasEntry().convertToTargetAddress(stepAddress, AliasEntry.AliasContext.create(step, context));
                } else {
                    useAddress = stepAddress;
                }

                final OperationTransformer transformer = target.resolveTransformer(context, useAddress, operationName);
                final PathAddress transformed = TransformersImpl.transformAddress(useAddress, target);
                // Update the operation using the new path address
                step.get(OP_ADDR).set(transformed.toModelNode()); // TODO should this happen by default?

                result = transformer.transformOperation(context, transformed, step);
            }
            final ModelNode transformedOperation = result.getTransformedOperation();
            if (transformedOperation != null) {
                composite.get(STEPS).add(transformedOperation);
                resultIdx++;
            }
            steps.add(new Step(stepIdx, resultIdx, result));
        }
        final CompositeResultTransformer resultHandler = new CompositeResultTransformer(steps);
        return new TransformedOperation(composite, resultHandler, resultHandler);
    }

    private static class CompositeResultTransformer implements OperationResultTransformer, OperationRejectionPolicy {

        private final List<Step> steps;
        private volatile Step failedStep;

        private CompositeResultTransformer(final List<Step> steps) {
            this.steps = steps;
        }

        // TODO WFCORE-624
        @Override
        public boolean rejectOperation(final ModelNode preparedResult) {
            for(final Step step : steps) {
                if(step.isDiscarded()) {
                    continue;
                }
                final String resultIdx = "step-" + step.getResultingIdx();
                // WFCORE-622 partial workaround. If there's no step-x node, assume it's a mismatch between
                // the result being checked and our list of steps. Don't modify the result.
                // This doesn't solve the problem of mismatched checks of steps that do exist,
                // but it prevents pollution of the result with spurious child nodes for irrelevant steps.
                final ModelNode stepResult = preparedResult.hasDefined(RESULT, resultIdx)
                        ? preparedResult.get(RESULT, resultIdx)
                        : new ModelNode();
                // ignored operations have no effect
                if(stepResult.hasDefined(OUTCOME) && IGNORED.equals(stepResult.get(OUTCOME).asString())) {
                    continue;
                }
                final TransformedOperation stepPolicy = step.getResult();
                if(stepPolicy.rejectOperation(stepResult)) {
                    // Only report the first failing step
                    failedStep = step;
                    return true;
                }
            }
            return false;
        }

        @Override
        public String getFailureDescription() {
            if(failedStep != null) {
                return failedStep.getResult().getFailureDescription();
            }
            return "";
        }

        // TODO WFCORE-624
        @Override
        public ModelNode transformResult(final ModelNode original) {
            final ModelNode response = original.clone();
            final ModelNode result = response.get(RESULT).setEmptyObject();
            boolean modified = false;
            for(final Step step : steps) {
                final String stepIdx = "step-" + step.getStepCount();
                // Set a successful result for discarded steps
                if(step.isDiscarded()) {
                    result.get(stepIdx).set(SUCCESSFUL);
                    continue;
                }

                final String resultIdx = "step-" + step.getResultingIdx();
                // WFCORE-622 partial workaround. If there's no step-x node, assume it's a mismatch between
                // the result being checked and our list of steps. Don't modify the result.
                // This doesn't solve the problem of mismatched checks of steps that do exist,
                // but it prevents pollution of the result with spurious child nodes for irrelevant steps.
                final ModelNode stepResult = original.hasDefined(RESULT, resultIdx)
                        ? original.get(RESULT, resultIdx) : new ModelNode();
                // Mark ignored steps as successful
                if(stepResult.hasDefined(OUTCOME) && IGNORED.equals(stepResult.get(OUTCOME).asString())) {
                    result.get(stepIdx).set(SUCCESSFUL);
                    modified = true;
                } else {
                    final OperationResultTransformer transformer = step.getResult();
                    // In case this is the failed step
                    if(step.getResult().rejectOperation(stepResult)) {
                        // Replace the response of the failed step
                        stepResult.get(OUTCOME).set(FAILED);
                        stepResult.get(FAILURE_DESCRIPTION).set(step.getResult().getFailureDescription());
                    }
                    ModelNode transformed = transformer.transformResult(stepResult);
                    if (transformed.isDefined() || original.has(RESULT, resultIdx)) {
                        result.get(stepIdx).set(transformer.transformResult(stepResult));
                        modified = true;
                    }
                }
            }
            return modified ? response : original;
        }
    }

    private static class Step {

        private final int stepCount;
        private final int resultingIdx;
        private final TransformedOperation result;

        private Step(int step, int resultingIdx, TransformedOperation result) {
            this.stepCount = step;
            this.resultingIdx = resultingIdx;
            this.result = result;
        }

        boolean isDiscarded() {
            return result.getTransformedOperation() == null;
        }

        int getResultingIdx() {
            return resultingIdx;
        }

        int getStepCount() {
            return stepCount;
        }

        TransformedOperation getResult() {
            return result;
        }

        @Override
        public String toString() {
            return "Step{" +
                    "step=" + stepCount +
                    ", operation=" + result.getTransformedOperation() +
                    '}';
        }
    }

}
