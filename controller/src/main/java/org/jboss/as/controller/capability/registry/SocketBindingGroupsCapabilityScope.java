/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability.registry;

/**
 * {@link CapabilityScope} specifically used for the {@code org.wildfly.domain.socket-binding-group} capability.
 * <p>
 * <strong>NOTE:</strong> This context is not used for child resources (subsystems) in the 'socket-binding-group'
 * part of the Host Controller resource tree.
 *
 * @author Brian Stansberry
 *
 * @see SocketBindingGroupChildScope
 */
class SocketBindingGroupsCapabilityScope implements CapabilityScope {

    static final SocketBindingGroupsCapabilityScope INSTANCE = new SocketBindingGroupsCapabilityScope();

    @Override
    public boolean canSatisfyRequirement(String requiredName, CapabilityScope dependentScope, CapabilityResolutionContext context) {
        return dependentScope instanceof SocketBindingGroupsCapabilityScope
                || dependentScope instanceof ServerGroupsCapabilityScope
                || dependentScope instanceof ServerConfigCapabilityScope;
    }

    @Override
    public boolean requiresConsistencyCheck() {
        return false;
    }

    @Override
    public String getName() {
        return "socket-binding-groups";
    }
}
