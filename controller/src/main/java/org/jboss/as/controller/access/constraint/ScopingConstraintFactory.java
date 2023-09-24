/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.constraint;

/**
 * Marker interface that indicates the constraints produced by this factory are associated
 * with a {@link ScopingConstraint}.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public interface ScopingConstraintFactory extends ConstraintFactory {
}
