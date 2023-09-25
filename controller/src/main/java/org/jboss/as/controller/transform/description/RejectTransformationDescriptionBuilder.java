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
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class RejectTransformationDescriptionBuilder extends AbstractTransformationDescriptionBuilder implements TransformationDescriptionBuilder {

    protected RejectTransformationDescriptionBuilder(PathElement pathElement) {
        super(pathElement, PathAddressTransformer.DEFAULT, ResourceTransformer.DEFAULT,
                OperationTransformer.DEFAULT, null);
    }

    @Override
    public TransformationDescription build() {
        final AttributeTransformationDescriptionBuilderImpl.AttributeTransformationDescriptionBuilderRegistry empty = new AttributeTransformationDescriptionBuilderImpl.AttributeTransformationDescriptionBuilderRegistry();
        return buildDefault(DiscardPolicy.REJECT_AND_WARN, true, empty);
    }

}
