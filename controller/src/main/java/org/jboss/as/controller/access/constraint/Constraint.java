/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.constraint;

import org.jboss.as.controller.access.Action;

/**
 * A constraint that can help govern whether access is allowed.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public interface Constraint {

    /**
     * Gets whether this constraint violates another constraint
     *
     * @param other the other constraint
     * @param actionEffect the effect being evaluated
     *
     * @return {@code true} if the combination of constraints is a violation
     */
    boolean violates(Constraint other, Action.ActionEffect actionEffect);

    /**
     * Gets whether this constraint is equivalent to and can thus replace another constraint
     * in a {@link org.jboss.as.controller.access.permission.ManagementPermission}.
     *
     * @param other the other constraint
     * @return {@code true} if replacement is valid
     */
    boolean replaces(Constraint other);
}
