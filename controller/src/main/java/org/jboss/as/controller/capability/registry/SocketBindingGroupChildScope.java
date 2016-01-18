/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
