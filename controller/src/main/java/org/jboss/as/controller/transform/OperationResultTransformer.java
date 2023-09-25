/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform;

import org.jboss.dmr.ModelNode;

/**
 * Transformer for the operation response. Despite the name of this interface, the transformation
 * is applied to the entire response node, not just any {@code result} field in that node.
 *
* @author Emanuel Muckenhuber
*/
@FunctionalInterface
public interface OperationResultTransformer {

    /**
     * Transform the operation result.
     *
     * @param response the operation response, including any {@code outcome}
     * @return the transformed response
     */
    ModelNode transformResult(ModelNode response);

    OperationResultTransformer ORIGINAL_RESULT = new OperationResultTransformer() {

        @Override
        public ModelNode transformResult(ModelNode response) {
            return response;
        }

    };

}
