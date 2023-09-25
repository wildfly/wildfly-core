/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability.registry;

/**
 * {@link CapabilityScope} used for capabilities scoped to a Host Controller {@code server-config} resource.
 *
 * @author Brian Stansberry
 */
class ServerGroupsCapabilityScope implements CapabilityScope {

    static final ServerGroupsCapabilityScope INSTANCE = new ServerGroupsCapabilityScope();

    @Override
    public boolean canSatisfyRequirement(String requiredName, CapabilityScope dependentScope, CapabilityResolutionContext context) {
        return dependentScope instanceof ServerConfigCapabilityScope;
    }

    @Override
    public boolean requiresConsistencyCheck() {
        return false;
    }

    @Override
    public String getName() {
        return "server-groups";
    }
}
