/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.PathAddressTransformer;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.version.Stability;

/**
 * Transformation builder discarding all operations to this resource.
 *
 * @author Emanuel Muckenhuber
 */
public final class DiscardTransformationDescriptionBuilder extends AbstractTransformationDescriptionBuilder {

    protected DiscardTransformationDescriptionBuilder(Stability stability, PathElement pathElement) {
        super(stability, pathElement, PathAddressTransformer.DEFAULT, ResourceTransformer.DISCARD, OperationTransformer.DISCARD, null);
    }

    @Override
    public TransformationDescription build() {
        final AttributeTransformationDescriptionBuilderImpl.AttributeTransformationDescriptionBuilderRegistry empty = new AttributeTransformationDescriptionBuilderImpl.AttributeTransformationDescriptionBuilderRegistry();
        return buildDefault(DiscardPolicy.SILENT, true, empty);
    }
}
