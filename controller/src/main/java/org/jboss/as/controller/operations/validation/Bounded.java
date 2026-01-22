/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

/**
 * Describes a bounded value.
 * @author Paul Ferraro
 */
public interface Bounded<T> {

    /**
     * Returns the lower bound of this bounded value, or null, if no lower bound exists.
     * @return the lower bound of this bounded value, or null, if no lower bound exists.
     */
    Bound<T> getLowerBound();

    /**
     * Returns the upper bound of this bounded value, or null, if no upper bound exists.
     * @return the upper bound of this bounded value, or null, if no upper bound exists.
     */
    Bound<T> getUpperBound();
}
