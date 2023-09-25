/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.constraint;

/**
 * Abstract superclass for {@link ConstraintFactory} implementations.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
abstract class AbstractConstraintFactory implements ConstraintFactory {


    @Override
    public int compareTo(ConstraintFactory o) {
        if (o instanceof  AbstractConstraintFactory) {
            // We have no particular preference, so defer to other
            int otherPref = ((AbstractConstraintFactory) o).internalCompare(this);
            if (otherPref == 0) {
                // Other also has no preference. We came first, so we stay first
                return this.equals(o) ? 0 : -1;
            }
            // Defer to other
            return otherPref * -1;
        }

        return -1;
    }

    /**
     * Compare this {@link AbstractConstraintFactory} to another. Similar contract to {@link Comparable#compareTo(Object)}
     * except that a return value of {@code 0} does not imply equality; rather it implies indifference with respect
     * to order. The intended use for this method is in {@link Comparable#compareTo(Object)} implementations where
     * the class implementing the method has no preference with respect to order and is willing to go with the
     * preference of the passed in object if it has one.
     *
     * @param other  the other constraint factory
     */
    protected abstract int internalCompare(AbstractConstraintFactory other);
}
