/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.constraint;

import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.JmxAction;
import org.jboss.as.controller.access.JmxTarget;
import org.jboss.as.controller.access.TargetAttribute;
import org.jboss.as.controller.access.TargetResource;
import org.jboss.as.controller.access.rbac.StandardRole;

/**
 * A factory for constraints.
 * <p>
 * <strong>Implementations of this interface should implement {@link #equals(Object)} and {@link #hashCode()}
 * such that two factories that produce the same constraints can be treated as equal in hash-based collections.</strong>
 * </p>
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public interface ConstraintFactory extends Comparable<ConstraintFactory> {

    /**
     * Provides a constraint suitable for the given {@code role} in the standard WildFly
     * role based access control system.
     *
     * @param role the role
     * @param actionEffect the {@link org.jboss.as.controller.access.Action.ActionEffect} for which the constraint is relevant
     *
     * @return the constraint. Cannot return {@code null}
     */
    Constraint getStandardUserConstraint(StandardRole role, Action.ActionEffect actionEffect);

    /**
     * Provides a constraint appropriate for the given {@code action} and {@code target}
     *
     *
     * @param actionEffect the {@link org.jboss.as.controller.access.Action.ActionEffect} for which the constraint is relevant
     * @param action the action
     * @param target the attribute that is the target of the action
     *
     * @return the constraint. Cannot return {@code null}
     */
    Constraint getRequiredConstraint(Action.ActionEffect actionEffect, Action action, TargetAttribute target);

    /**
     * Provides a constraint appropriate for the given {@code action} and {@code target}
     *
     *
     * @param actionEffect the {@link org.jboss.as.controller.access.Action.ActionEffect} for which the constraint is relevant
     * @param action the action
     * @param target the resource that is the target of the action
     *
     * @return the constraint. Cannot return {@code null}
     */
    Constraint getRequiredConstraint(Action.ActionEffect actionEffect, Action action, TargetResource target);

    /**
     * Provides a constraint appropriate for the given {@code action} and {@code target}
     *
     *
     * @param actionEffect the {@link org.jboss.as.controller.access.Action.ActionEffect} for which the constraint is relevant
     * @param action the action
     * @param target the jmx bean that is the target of the action
     *
     * @return the constraint. Cannot return {@code null}
     */
    Constraint getRequiredConstraint(Action.ActionEffect actionEffect, JmxAction action, JmxTarget target);
}
