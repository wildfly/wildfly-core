/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.PathAddressTransformer;
import org.jboss.as.controller.transform.ResourceTransformer;

/**
 * Transformation builder discarding all operations to this resource.
 *
 * @author Emanuel Muckenhuber
 */
public final class DiscardTransformationDescriptionBuilder extends AbstractTransformationDescriptionBuilder implements TransformationDescriptionBuilder {

    protected DiscardTransformationDescriptionBuilder(PathElement pathElement) {
        super(pathElement, PathAddressTransformer.DEFAULT, ResourceTransformer.DISCARD,
                OperationTransformer.DISCARD, null);
    }

    @Override
    public TransformationDescription build() {
        final AttributeTransformationDescriptionBuilderImpl.AttributeTransformationDescriptionBuilderRegistry empty = new AttributeTransformationDescriptionBuilderImpl.AttributeTransformationDescriptionBuilderRegistry();
        return buildDefault(DiscardPolicy.SILENT, true, empty);
    }

}
