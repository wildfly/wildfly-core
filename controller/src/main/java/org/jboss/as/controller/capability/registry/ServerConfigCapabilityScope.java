/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.capability.registry;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;

/**
 * {@link CapabilityScope} used for capabilities scoped to a Host Controller {@code server-config} resource.
 *
 * @author Brian Stansberry
 */
class ServerConfigCapabilityScope implements CapabilityScope {

    static final ServerConfigCapabilityScope INSTANCE = new ServerConfigCapabilityScope();

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
        return ModelDescriptionConstants.SERVER_CONFIG;
    }
}
