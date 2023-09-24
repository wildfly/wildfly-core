/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability.registry;

/**
 * {@link CapabilityScope} specifically used for the {@code org.wildfly.domain.profile} capability.
 * <p>
 * <strong>NOTE:</strong> This context is not used for child resources (subsystems) in the 'profile'
 * part of the Host Controller resource tree.
 *
 * @author Brian Stansberry
 */
class ProfilesCapabilityScope implements CapabilityScope {

    public static final ProfilesCapabilityScope INSTANCE = new ProfilesCapabilityScope();

    @Override
    public boolean canSatisfyRequirement(String requiredName, CapabilityScope dependentScope, CapabilityResolutionContext context) {
        return dependentScope instanceof ProfilesCapabilityScope || dependentScope instanceof ServerGroupsCapabilityScope;
    }

    @Override
    public boolean requiresConsistencyCheck() {
        return false;
    }

    @Override
    public String getName() {
        return "profiles";
    }
}
