/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability.registry;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST;

/**
 * {@link CapabilityScope} for capabilities whose use is restricted to
 * the Host Controller in which they are installed; e.g. they cannot be
 * resolved by requirers in a domain level context.
 *
 * @author Brian Stansberry
 */
class HostCapabilityScope implements CapabilityScope {

    static final HostCapabilityScope INSTANCE = new HostCapabilityScope();

    @Override
    public boolean canSatisfyRequirement(String requiredName, CapabilityScope dependentScope, CapabilityResolutionContext context) {
        return dependentScope == CapabilityScope.GLOBAL || dependentScope instanceof HostCapabilityScope;
    }

    @Override
    public boolean requiresConsistencyCheck() {
        return false;
    }

    @Override
    public String getName() {
        return HOST;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || !(o == null || getClass() != o.getClass());
    }
}
