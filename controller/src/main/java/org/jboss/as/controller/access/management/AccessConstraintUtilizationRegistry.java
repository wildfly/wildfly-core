/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.management;

import java.util.Map;

import org.jboss.as.controller.PathAddress;

/**
 * Registry for tracking usage of {@link AccessConstraintDefinition}s.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public interface AccessConstraintUtilizationRegistry {

    Map<PathAddress, AccessConstraintUtilization> getAccessConstraintUtilizations(AccessConstraintKey accessConstraintKey);

    void registerAccessConstraintResourceUtilization(AccessConstraintKey key, PathAddress address);

    void registerAccessConstraintAttributeUtilization(AccessConstraintKey key,
                                                      PathAddress address, String attribute);

    void registerAccessConstraintOperationUtilization(AccessConstraintKey key,
                                                      PathAddress address, String operation);

    void unregisterAccessConstraintUtilizations(PathAddress address);
}
