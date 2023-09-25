/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.management;

import java.util.Locale;

import org.jboss.as.controller.access.constraint.ConstraintFactory;
import org.jboss.dmr.ModelNode;

/**
 * Definition of a constraint that can be associated with a
 * {@link org.jboss.as.controller.ResourceDefinition}, {@link org.jboss.as.controller.OperationDefinition}
 * or {@link org.jboss.as.controller.AttributeDefinition}.
 * <p>
 * Implementations of this class must be usable as keys in a map; i.e. they should have proper
 * implementations of {@link #equals(Object)} and {@link #hashCode()}.
 * </p>
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public interface AccessConstraintDefinition {

    /**
     * Get the name of the constraint
     *
     * @return the name
     */
    String getName();

    /**
     * Get the type of constraint
     *
     * @return the type of constraint
     */
    String getType();

    /**
     * Gets whether the definition is provided by the core management system.
     * @return {@code true} if the definition is provided by the core; {@code false} if it
     *         is provided by a subsystem
     */
    boolean isCore();

    /**
     * Gets the name of the subsystem that provides this definition, it is not {@link #isCore() core}.
     *
     * @return the subsystem name, or {@code null} if {@link #isCore()}
     */
    String getSubsystemName();

    /**
     * Gets a unique identifier for this {@code AccessConstraintDefinition}.
     *
     * @return the key. Will not be {@code null}
     */
    AccessConstraintKey getKey();

    /**
     * Gets a text description if this attribute definition for inclusion in read-xxx-description metadata.
     *
     * @param locale locale to use to provide internationalized text
     *
     * @return the text description, or {@code null} if none is available
     */
    String getDescription(Locale locale);

    /**
     * Get arbitrary descriptive information about the constraint for inclusion
     * in the read-xxx-description metadata
     *
     * @param locale locale to use for any internationalized text
     *
     * @return an arbitrary description node; can be {@code null} or undefined
     */
    ModelNode getModelDescriptionDetails(Locale locale);

    /**
     * Get the factory to use for creating a {@link org.jboss.as.controller.access.constraint.Constraint} that
     * implements
     * @return the factory. Cannot return {@code null}
     */
    ConstraintFactory getConstraintFactory();
}
