/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import org.jboss.as.controller.transform.OperationTransformer;

/**
 * Transformation builder interface for overriding a given operation. The phases described in the super-interface apply here as well.
 *
 * @author Emanuel Muckenhuber
 */
public interface OperationTransformationOverrideBuilder extends BaseAttributeTransformationDescriptionBuilder<OperationTransformationOverrideBuilder> {

    /**
     * Give the operation a new name
     *
     * @param newName the new name of the operation
     * @return this operation transformer builder
     */
    OperationTransformationOverrideBuilder rename(String newName);

    /**
     * Set an optional operation transformer, which is called after all attribute rules added by the super-interface have
     * been executed.
     *
     * @param operationTransformer the operation transformer
     * @return this operation transformer builder
     */
    OperationTransformationOverrideBuilder setCustomOperationTransformer(OperationTransformer operationTransformer);

    /**
     * Inherit all existing attribute rules from the resource for this operation transformer.
     *
     * @return this operation transformer builder
     */
    OperationTransformationOverrideBuilder inheritResourceAttributeDefinitions();

    /**
     * Reject this operation
     *
     * @return this operation transformer builder
     */
    OperationTransformationOverrideBuilder setReject();

}
