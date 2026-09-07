/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller;

import org.jboss.dmr.ModelNode;

/**
 * An implementation of this interface will be invoked before
 * a new attribute value is set, so it has a chance to adjust the new value,
 * if and as necessary, e.g. propagate properties from the current value
 * in case the new value is missing them.
 * The implementation of this interface will be invoked before
 * the new value is validated by the attribute's parameter validator.
 * Which means after the value has been corrected by an instance of
 * this interface, the corrected value will be passed to the
 * attribute's parameter validator for validation.
 *
 * @author Alexey Loubyansky
 */
@FunctionalInterface
public interface ParameterCorrector {

    /**
     * Adjusts the value to be set on the attribute.
     *
     * @param newValue  the new value to be set
     * @param currentValue  the current value of the attribute
     * @return  the value that actually should be set
     */
    ModelNode correct(ModelNode newValue, ModelNode currentValue);
}
