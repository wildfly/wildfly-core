/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.transform.description;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.transform.PathAddressTransformer;

/**
 * @author Emanuel Muckenhuber
 */
abstract class AbstractDescription implements TransformationDescription {

    private final boolean inherited;
    private final PathAddressTransformer pathAddressTransformer;
    protected final PathElement pathElement;

    AbstractDescription(final PathElement pathElement, final PathAddressTransformer transformation) {
        this(pathElement, transformation, false);
    }

    AbstractDescription(final PathElement pathElement, final PathAddressTransformer transformation, final boolean inherited) {
        this.pathElement = pathElement;
        this.pathAddressTransformer = transformation;
        this.inherited = inherited;
    }

    @Override
    public PathElement getPath() {
        return pathElement;
    }

    @Override
    public PathAddressTransformer getPathAddressTransformer() {
        return pathAddressTransformer;
    }

    @Override
    public boolean isInherited() {
        return inherited;
    }
}
