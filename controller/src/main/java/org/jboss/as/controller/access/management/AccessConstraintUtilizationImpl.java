/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.access.management;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.PathAddress;

/**
 * {@link AccessConstraintUtilization} implementation.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
class AccessConstraintUtilizationImpl implements AccessConstraintUtilization {

    private final AccessConstraintKey constraint;
    private final PathAddress pathAddress;
    private volatile Boolean resourceConstrained;
    private Set<String> attributes = Collections.synchronizedSet(new HashSet<String>());
    private Set<String> operations = Collections.synchronizedSet(new HashSet<String>());

    public AccessConstraintUtilizationImpl(AccessConstraintKey constraint, PathAddress pathAddress) {
        this.constraint = constraint;
        this.pathAddress = pathAddress;
    }

    @Override
    public PathAddress getPathAddress() {
        return pathAddress;
    }

    @Override
    public boolean isEntireResourceConstrained() {
        final Boolean constrained = resourceConstrained;
        return constrained == null ? false : constrained;
    }

    @Override
    public Set<String> getAttributes() {
        return Collections.unmodifiableSet(attributes);
    }

    @Override
    public Set<String> getOperations() {
        return Collections.unmodifiableSet(operations);
    }

    @Override
    public int hashCode() {
        return pathAddress.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null
                && obj instanceof AccessConstraintUtilizationImpl
                && pathAddress.equals(((AccessConstraintUtilizationImpl) obj).pathAddress);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{pathAddress=" + pathAddress + '}';
    }

    void setResourceConstrained(boolean resourceConstrained) {
        this.resourceConstrained = resourceConstrained;
    }

    void addAttribute(String attribute) {
        attributes.add(attribute);
    }

    void addOperation(String operation) {
        operations.add(operation);
    }
}
