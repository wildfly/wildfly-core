/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.management;

import java.util.Set;

import org.jboss.as.controller.PathAddress;

/**
 * Provides information about how an {@link AccessConstraintDefinition} is utilized for a particular
 * {@link org.jboss.as.controller.registry.ImmutableManagementResourceRegistration management resource registration}.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public interface AccessConstraintUtilization {

    /**
     * Gets the address under which the resource registrations is registered.
     *
     * @return the address. Will not be {@code null}
     */
    PathAddress getPathAddress();

    /**
     * Gets whether the constraint applies to the resource as a whole
     * @return  {@code true} if the entire resource is constrained; {@code false} if the constraint only applies
     *          to attributes or operations
     */
    boolean isEntireResourceConstrained();

    /**
     * Gets the names of any attributes that are specifically constrained.
     * @return  the attribute names, or an empty set. Will not be {@code null}
     */
    Set<String> getAttributes();

    /**
     * Gets the names of any operations that are specifically constrained.
     * @return  the operation names, or an empty set. Will not be {@code null}
     */
    Set<String> getOperations();
}
