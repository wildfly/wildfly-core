/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform;

import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;

/**
 * Base class for {@code OperationTransformer} implementations, which don't need to transform the operation result.
 *
 * @author Emanuel Muckenhuber
 */
public abstract class AbstractOperationTransformer implements OperationTransformer, OperationResultTransformer {

    /**
     * Transform the operation only.
     *
     * @param context the transformation context
     * @param address the operation address
     * @param operation the original operation
     * @return the transformed operation
     */
    protected abstract ModelNode transform(final TransformationContext context, final PathAddress address, final ModelNode operation);

    @Override
    public TransformedOperation transformOperation(final TransformationContext context, final PathAddress address, final ModelNode operation) {
        final ModelNode transformedOperation = transform(context, address, operation);
        return new TransformedOperation(transformedOperation, this);
    }

    @Override
    public ModelNode transformResult(final ModelNode result) {
        return OperationResultTransformer.ORIGINAL_RESULT.transformResult(result);
    }

}
