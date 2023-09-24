/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability.registry;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;

import java.util.Map;
import java.util.Set;

/**
 * {@link CapabilityScope} for the children of a Host Controller {@code profile} resource.
 * Note this does not include the profile capability itself.
 *
 * @author Brian Stansberry
 *
 * @see ProfilesCapabilityScope
 */
class ProfileChildCapabilityScope extends IncludingResourceCapabilityScope {

    private static final CapabilityResolutionContext.AttachmentKey<Map<String, Set<CapabilityScope>>> PROFILE_KEY =
            CapabilityResolutionContext.AttachmentKey.create(Map.class);

    ProfileChildCapabilityScope(String value) {
        super(PROFILE_KEY, PROFILE, value);
    }

    @Override
    public boolean canSatisfyRequirement(String requiredName, CapabilityScope dependentScope, CapabilityResolutionContext context) {
        boolean result = equals(dependentScope);
        if (!result && dependentScope instanceof ProfileChildCapabilityScope) {
            Set<CapabilityScope> includers = getIncludingScopes(context);
            result = includers.contains(dependentScope);
        }
        return result;
    }

    @Override
    public boolean requiresConsistencyCheck() {
        return false;
    }

    @Override
    protected CapabilityScope createIncludedContext(String name) {
        return new ProfileChildCapabilityScope(name);
    }
}
