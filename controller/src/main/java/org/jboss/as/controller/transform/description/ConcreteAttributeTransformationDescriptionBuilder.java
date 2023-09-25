/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

/**
 * A concrete implementation of the {@link BaseAttributeTransformationDescriptionBuilder}.
 *
 * @author Emanuel Muckenhuber
 */
class ConcreteAttributeTransformationDescriptionBuilder extends AttributeTransformationDescriptionBuilderImpl<AttributeTransformationDescriptionBuilder> implements AttributeTransformationDescriptionBuilder {

    protected ConcreteAttributeTransformationDescriptionBuilder(ResourceTransformationDescriptionBuilder builder, AttributeTransformationDescriptionBuilderRegistry registry) {
        super(builder, registry);
    }

    @Override
    protected AttributeTransformationDescriptionBuilder thisBuilder() {
        return this;
    }

}
