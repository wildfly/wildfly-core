/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.constraint;

/**
 * Base class for {@link Constraint} implementations.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public abstract class AbstractConstraint implements Constraint {

    protected AbstractConstraint() {
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * This default implementation always returns {@code false}.
     * </p>
     *
     * @return {@code false}, always
     */
    @Override
    public boolean replaces(Constraint other) {
        return false;
    }
}
