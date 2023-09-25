/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.permission;

import java.security.Permission;
import java.util.List;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.constraint.Constraint;

/**
 * Simple implementation of {@link ManagementPermission}.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class SimpleManagementPermission extends ManagementPermission {

    private final Constraint[] constraints;
    /**
     * Constructs a permission with the specified name.
     */
    public SimpleManagementPermission(Action.ActionEffect actionEffect, List<Constraint> constraints) {
        this(actionEffect, constraints.toArray(new Constraint[constraints.size()]));
    }

    public SimpleManagementPermission(Action.ActionEffect actionEffect, Constraint... constraints) {
        super("SimpleManagementPermission", actionEffect);
        this.constraints = constraints;
    }

    @Override
    public boolean implies(Permission permission) {
        if (equals(permission)) {
            SimpleManagementPermission other = (SimpleManagementPermission) permission;
            // Validate constraints
            assert constraints.length == other.constraints.length : String.format("incompatible ManagementPermission; " +
                    "differing constraint counts %d vs %d", constraints.length, other.constraints.length);
            Action.ActionEffect actionEffect = getActionEffect();
            for (int i = 0; i < constraints.length; i++) {
                Constraint ours = constraints[i];
                Constraint theirs = other.constraints[i];
                assert ours.getClass() == theirs.getClass() : "incompatible constraints: ours = " + ours.getClass() + " -- theirs = " + theirs.getClass();
                if (ours.violates(theirs, actionEffect)) {
                    ControllerLogger.ACCESS_LOGGER.tracef("Constraints are violated for %s", actionEffect);
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ManagementPermission that = (ManagementPermission) o;

        return getActionEffect() == that.getActionEffect();

    }

    @Override
    public int hashCode() {
        return getActionEffect().hashCode();
    }

    @Override
    public String getActions() {
        return getActionEffect().toString();
    }

    public ManagementPermission createScopedPermission(Constraint constraint, int constraintIndex) {
        Constraint[] altered;
        if (constraintIndex == constraints.length) {
            altered = new Constraint[constraintIndex + 1];
            System.arraycopy(constraints, 0, altered, 0, constraints.length);
        } else {
            Constraint existing = constraints[constraintIndex];
            if (constraint.replaces(existing)) {
                altered = new Constraint[constraints.length];
                System.arraycopy(constraints, 0, altered, 0, constraints.length);
            } else {
                altered = new Constraint[constraintIndex + 1];
                if (constraintIndex == 0) {
                    System.arraycopy(constraints, 0, altered, 1, constraints.length);
                } else {
                    System.arraycopy(constraints, 0, altered, 0, constraintIndex);
                    System.arraycopy(constraints, constraintIndex, altered, constraintIndex + 1, constraints.length - constraintIndex);
                }
            }
        }
        altered[constraintIndex] = constraint;
        return new SimpleManagementPermission(getActionEffect(), altered);
    }
}
