/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability.registry;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;

import java.util.Map;
import java.util.Set;

/**
 * {@link CapabilityScope} for the children of a Host Controller {@code socket-binding-group} resource.
 * Note this does not include the socket binding group capability itself.
 *
 * @author Brian Stansberry
 *
 * @see SocketBindingGroupsCapabilityScope
 */
class SocketBindingGroupChildScope extends IncludingResourceCapabilityScope {

    private static final CapabilityResolutionContext.AttachmentKey<Map<String, Set<CapabilityScope>>> SBG_KEY =
            CapabilityResolutionContext.AttachmentKey.create(Map.class);

    SocketBindingGroupChildScope(String value) {
        super(SBG_KEY, SOCKET_BINDING_GROUP, value);
    }

    @Override
    public boolean canSatisfyRequirement(String requiredName, CapabilityScope dependentScope, CapabilityResolutionContext context) {
        boolean result;
        if (dependentScope instanceof SocketBindingGroupChildScope) {
            result = equals(dependentScope);
            if (!result) {
                Set<CapabilityScope> includers = getIncludingScopes(context);
                result = includers.contains(dependentScope);
            }
        } else {
            result = !(dependentScope instanceof ProfilesCapabilityScope) && !(dependentScope instanceof ServerGroupsCapabilityScope);
        }
        return result;
    }

    @Override
    public boolean requiresConsistencyCheck() {
        return true;
    }

    @Override
    protected CapabilityScope createIncludedContext(String name) {
        return new SocketBindingGroupChildScope(name);
    }
}
