/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.validation;

/**
 * A validator that checks whether the node complies with some sort of minimum or maximum.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public interface MinMaxValidator {

    /**
     * Gets the minimum, if there is one.
     *
     * @return the minimum value, or {@code null} if there is no minimum
     */
    Long getMin();

    /**
     * Gets the maximum, if there is one.
     *
     * @return the maximum value, or {@code null} if there is no minimum
     */
    Long getMax();
}
