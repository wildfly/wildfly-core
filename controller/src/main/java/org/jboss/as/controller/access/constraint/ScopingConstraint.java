/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.constraint;

/**
 * Interface used to indicate that the implementing class serves to limit the scope of an
 * otherwise standard permission.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public interface ScopingConstraint {

    /** Get the factory that produces constraints of this type */
    ScopingConstraintFactory getFactory();

    Constraint getStandardConstraint();

    /** Get a constraint that should be used for reads of resources that are outside the scope of the constraint. */
    Constraint getOutofScopeReadConstraint();
}
